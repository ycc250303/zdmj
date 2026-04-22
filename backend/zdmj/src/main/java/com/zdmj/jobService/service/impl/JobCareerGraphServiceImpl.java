package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.common.util.PromptUtil.JobRole;
import com.zdmj.jobService.dto.JobCareerGraphDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.JobCareerGraph;
import com.zdmj.jobService.mapper.JobCareerGraphMapper;
import com.zdmj.jobService.service.JobCareerGraphService;
import com.zdmj.jobService.service.JobService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 岗位关联图谱服务实现（DB 持久化版本）。
 *
 * <p>整体流程：岗位识别（关键词 + LLM 兜底） → 选取对应图谱提示词 → 结构化调用 LLM →
 * 图谱规范校验（晋升路径 ≥3 节点、换岗路径 ≥5 条且每条 ≥2 节点） → 写入 {@code job_career_graphs}
 * 表（1 个岗位最多 1 条记录，存在则 update、否则 insert）。</p>
 *
 * <p>与岗位能力画像 {@link JobCapabilityProfileServiceImpl} 保持完全一致的结构：
 * {@link ServiceImpl} + {@code getOne/save/updateById}，JSONB 列由 Jackson 序列化。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobCareerGraphServiceImpl extends ServiceImpl<JobCareerGraphMapper, JobCareerGraph>
        implements JobCareerGraphService {

    /** 晋升路径最少节点数（任务书："岗位未来发展路径" 体现层级感） */
    private static final int MIN_VERTICAL_PATH_NODES = 3;
    /** 换岗路径的最小条数（任务书硬性要求） */
    private static final int MIN_TRANSITION_PATHS = 5;
    /** 单条换岗路径的最少节点数（任务书硬性要求） */
    private static final int MIN_NODES_PER_TRANSITION_PATH = 2;
    /** 关键词直出阈值，与岗位能力画像保持一致 */
    private static final int KEYWORD_DIRECT_HIT_THRESHOLD = 4;

    /**
     * 岗位关键词表（与 {@link JobCapabilityProfileServiceImpl} 保持一致）。
     */
    private static final Map<JobRole, List<String>> KEYWORDS = Map.of(
            JobRole.JAVA, List.of("java", "spring", "spring boot", "mybatis", "mysql", "redis", "jvm"),
            JobRole.FRONTEND,
            List.of("react", "vue", "typescript", "javascript", "webpack", "vite", "css", "html"),
            JobRole.CPP, List.of("c++", "cpp", "stl", "cmake", "gdb", "linux", "多线程", "内存"),
            JobRole.SOFTWARE_TEST,
            List.of("测试", "test case", "pytest", "selenium", "jmeter", "postman", "缺陷"),
            JobRole.AI_AGENT, List.of("llm", "大模型", "agent", "rag", "langchain", "prompt", "embedding"),
            JobRole.ALGORITHM, List.of("算法", "machine learning", "深度学习", "pytorch", "tensorflow"),
            JobRole.DATA_ANALYST, List.of("数据分析", "sql", "tableau", "powerbi", "excel", "指标"),
            JobRole.BIG_DATA, List.of("hadoop", "spark", "flink", "hive", "数仓", "kafka"),
            JobRole.DEVOPS_SRE, List.of("devops", "sre", "k8s", "kubernetes", "docker", "ci/cd", "ansible"),
            JobRole.CYBERSECURITY, List.of("安全", "渗透", "漏洞", "owasp", "攻防", "合规"));

    private final JobService jobService;
    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;

    @Override
    public JobCareerGraphDTO getOrNull(Long jobId) {
        jobService.getDetail(jobId);
        JobCareerGraph entity = getOne(
                new LambdaQueryWrapper<JobCareerGraph>().eq(JobCareerGraph::getJobId, jobId));
        return entity == null ? null : toDto(entity);
    }

    @Override
    public JobCareerGraphDTO generate(Long jobId) {
        JobListItemDTO jobDetail = jobService.getDetail(jobId);

        String jobContext = buildJobContext(jobDetail);
        JobRole role = detectRole(jobContext);
        String promptName = PromptUtil.getJobCareerGraphPromptName(role);
        log.info("生成岗位关联图谱: jobId={}, role={}, prompt={}", jobId, role, promptName);

        JobCareerGraphDTO aiResult;
        try {
            aiResult = chatUtil.chatStructuredOnce(jobContext, promptName, null, JobCareerGraphDTO.class);
        } catch (Exception e) {
            log.error("岗位关联图谱生成失败: jobId={}, role={}, prompt={}", jobId, role, promptName, e);
            throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_GENERATION_FAILED);
        }

        validateGraph(aiResult);

        aiResult.setJobId(jobId);
        aiResult.setTargetRoleType(PromptUtil.getPromptDisplayType(promptName));
        markCurrentNode(aiResult);

        JobCareerGraph existing = getOne(
                new LambdaQueryWrapper<JobCareerGraph>().eq(JobCareerGraph::getJobId, jobId));
        JobCareerGraph entity = toEntity(aiResult);
        entity.setJobId(jobId);
        entity.setPromptName(promptName);
        entity.setTargetRoleType(PromptUtil.getPromptDisplayType(promptName));
        entity.setRoleConfidence(BigDecimal.valueOf(estimateRoleConfidence(role, jobContext)));
        if (existing != null) {
            entity.setId(existing.getId());
            updateById(entity);
        } else {
            save(entity);
        }

        return aiResult;
    }

    /**
     * 规范性校验：不满足任务书要求（垂直 ≥3、换岗 ≥5 且每条 ≥2 节点）则抛出业务异常。
     */
    private void validateGraph(JobCareerGraphDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_INVALID);
        }
        List<JobCareerGraphDTO.VerticalPathNode> vertical = dto.getVerticalPath();
        List<JobCareerGraphDTO.TransitionPath> transitions = dto.getTransitionPaths();

        if (vertical == null || vertical.size() < MIN_VERTICAL_PATH_NODES) {
            log.warn("岗位图谱校验失败：垂直路径节点数不足，actual={}, min={}",
                    vertical == null ? 0 : vertical.size(), MIN_VERTICAL_PATH_NODES);
            throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_INVALID);
        }
        if (transitions == null || transitions.size() < MIN_TRANSITION_PATHS) {
            log.warn("岗位图谱校验失败：换岗路径条数不足，actual={}, min={}",
                    transitions == null ? 0 : transitions.size(), MIN_TRANSITION_PATHS);
            throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_INVALID);
        }
        for (int i = 0; i < transitions.size(); i++) {
            JobCareerGraphDTO.TransitionPath path = transitions.get(i);
            int nodeCount = path == null || path.getNodes() == null ? 0 : path.getNodes().size();
            if (nodeCount < MIN_NODES_PER_TRANSITION_PATH) {
                log.warn("岗位图谱校验失败：第 {} 条换岗路径节点数不足，actual={}, min={}",
                        i + 1, nodeCount, MIN_NODES_PER_TRANSITION_PATH);
                throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_INVALID);
            }
        }
    }

    /**
     * 标记"当前岗位"节点：
     * 若 {@link JobCareerGraphDTO#getCurrentNode()} 缺失 level，则取垂直路径中间层级作为起点；
     * 同时在 {@code verticalPath} 中对应节点的 {@code current} 置为 true，便于前端可视化。
     */
    private void markCurrentNode(JobCareerGraphDTO dto) {
        List<JobCareerGraphDTO.VerticalPathNode> vertical = dto.getVerticalPath();
        JobCareerGraphDTO.CurrentNode current = dto.getCurrentNode();

        int currentLevel = (current != null && current.getLevel() != null) ? current.getLevel() : -1;
        if (currentLevel < 0) {
            for (JobCareerGraphDTO.VerticalPathNode node : vertical) {
                if (Boolean.TRUE.equals(node.getCurrent()) && node.getLevel() != null) {
                    currentLevel = node.getLevel();
                    break;
                }
            }
        }
        if (currentLevel < 0 && !vertical.isEmpty()) {
            int mid = Math.min(vertical.size() - 1, vertical.size() / 2);
            Integer fallback = vertical.get(mid).getLevel();
            currentLevel = fallback != null ? fallback : mid + 1;
        }

        for (JobCareerGraphDTO.VerticalPathNode node : vertical) {
            node.setCurrent(node.getLevel() != null && node.getLevel() == currentLevel);
        }

        if (current == null) {
            current = new JobCareerGraphDTO.CurrentNode();
            dto.setCurrentNode(current);
        }
        if (current.getLevel() == null) {
            current.setLevel(currentLevel);
        }
        if (!StringUtils.hasText(current.getTitle()) && !vertical.isEmpty()) {
            final int finalLevel = currentLevel;
            vertical.stream()
                    .filter(n -> n.getLevel() != null && n.getLevel() == finalLevel)
                    .findFirst()
                    .ifPresent(n -> dto.getCurrentNode().setTitle(n.getTitle()));
        }
    }

    /**
     * 关键词 + LLM 兜底的岗位识别，与 {@link JobCapabilityProfileServiceImpl#detectRole} 保持一致。
     */
    private JobRole detectRole(String text) {
        if (!StringUtils.hasText(text)) {
            return JobRole.UNKNOWN;
        }
        String lower = text.toLowerCase();
        JobRole bestRole = JobRole.UNKNOWN;
        int bestScore = 0;
        for (Map.Entry<JobRole, List<String>> entry : KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestRole = entry.getKey();
            }
        }
        if (bestScore >= KEYWORD_DIRECT_HIT_THRESHOLD) {
            return bestRole;
        }
        try {
            RoleDetectLLMResult llmResult = chatUtil.chatStructuredOnce(text, PromptUtil.PromptNames.JOB_DETECT,
                    null, RoleDetectLLMResult.class);
            JobRole role = PromptUtil.getJobRoleByString(llmResult.getRoleCode());
            return role == JobRole.UNKNOWN ? bestRole : role;
        } catch (Exception e) {
            log.warn("岗位类型识别失败，回退关键词规则: {}", e.getMessage());
            return bestRole;
        }
    }

    /** 置信度估算，对齐 {@link JobCapabilityProfileServiceImpl#estimateRoleConfidence} */
    private double estimateRoleConfidence(JobRole role, String text) {
        if (role == null || role == JobRole.UNKNOWN || !StringUtils.hasText(text)) {
            return 0.2;
        }
        String lower = text.toLowerCase();
        int hit = 0;
        for (String kw : KEYWORDS.getOrDefault(role, List.of())) {
            if (lower.contains(kw.toLowerCase())) {
                hit++;
            }
        }
        return Math.min(0.95, 0.35 + hit * 0.1);
    }

    /** DTO → Entity（JSONB 列 JSON 文本化） */
    private JobCareerGraph toEntity(JobCareerGraphDTO dto) {
        JobCareerGraph entity = new JobCareerGraph();
        entity.setSummary(dto.getSummary());
        entity.setCurrentNode(toJson(dto.getCurrentNode()));
        entity.setVerticalPath(toJson(dto.getVerticalPath()));
        entity.setTransitionPaths(toJson(dto.getTransitionPaths()));
        return entity;
    }

    /** Entity → DTO（JSONB 列回填为强类型对象） */
    private JobCareerGraphDTO toDto(JobCareerGraph entity) {
        JobCareerGraphDTO dto = new JobCareerGraphDTO();
        dto.setJobId(entity.getJobId());
        dto.setTargetRoleType(StringUtils.hasText(entity.getTargetRoleType()) ? entity.getTargetRoleType()
                : PromptUtil.getPromptDisplayType(entity.getPromptName()));
        dto.setSummary(entity.getSummary());
        try {
            if (StringUtils.hasText(entity.getCurrentNode())) {
                dto.setCurrentNode(
                        objectMapper.readValue(entity.getCurrentNode(), JobCareerGraphDTO.CurrentNode.class));
            }
            if (StringUtils.hasText(entity.getVerticalPath())) {
                dto.setVerticalPath(objectMapper.readValue(entity.getVerticalPath(),
                        new TypeReference<List<JobCareerGraphDTO.VerticalPathNode>>() {
                        }));
            }
            if (StringUtils.hasText(entity.getTransitionPaths())) {
                dto.setTransitionPaths(objectMapper.readValue(entity.getTransitionPaths(),
                        new TypeReference<List<JobCareerGraphDTO.TransitionPath>>() {
                        }));
            }
        } catch (Exception e) {
            log.warn("反序列化岗位关联图谱 JSON 失败，jobId={}, err={}", entity.getJobId(), e.getMessage());
        }
        return dto;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("岗位图谱 JSON 序列化失败，字段将置空: {}", e.getMessage());
            return null;
        }
    }

    private String buildJobContext(JobListItemDTO job) {
        return """
                这是待分析的岗位信息（请基于它生成岗位关联图谱，包含岗位晋升路径与跨岗位转岗路径）：
                岗位名称：%s
                公司名称：%s
                工作地点：%s
                薪资：%s
                岗位描述：%s
                岗位职责：%s
                岗位要求：%s
                关键词：%s
                公司行业：%s
                """.formatted(
                valueOrNA(job.getJobName()),
                valueOrNA(job.getCompanyName()),
                valueOrNA(job.getLocation()),
                valueOrNA(job.getSalary()),
                valueOrNA(job.getDescription()),
                joinList(job.getJobDuties()),
                joinList(job.getJobRequirements()),
                joinList(job.getKeywords()),
                joinList(job.getCompanyIndustries()));
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "未提供";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((a, b) -> a + "；" + b)
                .orElse("未提供");
    }

    private static String valueOrNA(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未提供";
    }

    /** 与 {@link JobCapabilityProfileServiceImpl} 中同名结构保持一致，仅用于 LLM 响应解析 */
    @lombok.Data
    private static class RoleDetectLLMResult {
        private String roleCode;
        private double confidence;
        private String reason;
    }
}
