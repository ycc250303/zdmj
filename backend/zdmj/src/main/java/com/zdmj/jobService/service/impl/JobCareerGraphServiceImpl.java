package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.JobRole;
import com.zdmj.common.ai.PromptScenario;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobCareerGraphResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.entity.JobCareerGraph;
import com.zdmj.jobService.mapper.JobCareerGraphMapper;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobCareerGraphService;
import com.zdmj.jobService.service.JobService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 岗位关联图谱服务实现（DB 持久化版本）。
 *
 * <p>整体流程：复用岗位画像已识别的 {@code targetRoleType}（缺失则先生成画像）
 * → 选取对应图谱提示词 → 结构化调用 LLM →
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

    private final JobService jobService;
    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;
    private final PromptUtil promptUtil;
    private final JobCapabilityProfileService jobCapabilityProfileService;

    @Override
    public JobCareerGraphResponse getOrNull(Long jobId) {
        jobService.getDetail(jobId);
        JobCareerGraph entity = getOne(
                new LambdaQueryWrapper<JobCareerGraph>().eq(JobCareerGraph::getJobId, jobId));
        return entity == null ? null : toDto(entity);
    }

    @Override
    public JobCareerGraphResponse generate(Long jobId) {
        Long userId = UserHolder.requireUserId();
        JobListItemResponse jobDetail = jobService.getDetail(jobId);

        String jobContext = JobAnalysisSupport.buildJobContext(
                jobDetail,
                "这是待分析的岗位信息（请基于它生成岗位关联图谱，包含岗位晋升路径与跨岗位转岗路径）：");
        JobCapabilityProfileResponse jobProfile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        if (jobProfile == null) {
            log.info("岗位画像缺失，生成图谱前先识别并生成画像: jobId={}", jobId);
            jobProfile = jobCapabilityProfileService.getJobCapabilityProfile(jobId);
        }
        JobRole role = JobRole.fromString(jobProfile.getTargetRoleType());
        String promptName = promptUtil.resolve(PromptScenario.JOB_CAREER_GRAPH, role);
        log.info("生成岗位关联图谱: jobId={}, role={}, prompt={}", jobId, role, promptName);

        JobCareerGraphResponse aiResult;
        try {
            aiResult = chatUtil.chatStructuredOnce(userId, jobContext, promptName, null, JobCareerGraphResponse.class);
        } catch (Exception e) {
            log.error("岗位关联图谱生成失败: jobId={}, role={}, prompt={}", jobId, role, promptName, e);
            throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_GENERATION_FAILED);
        }

        validateGraph(aiResult);

        aiResult.setJobId(jobId);
        aiResult.setTargetRoleType(role.slug());
        markCurrentNode(aiResult);

        JobCareerGraph existing = getOne(
                new LambdaQueryWrapper<JobCareerGraph>().eq(JobCareerGraph::getJobId, jobId));
        JobCareerGraph entity = toEntity(aiResult);
        entity.setJobId(jobId);
        entity.setPromptName(promptName);
        entity.setTargetRoleType(role.slug());
        entity.setRoleConfidence(jobProfile.getRoleConfidence() != null
                ? jobProfile.getRoleConfidence()
                : BigDecimal.valueOf(0.2));
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
    private void validateGraph(JobCareerGraphResponse dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.JOB_CAREER_GRAPH_INVALID);
        }
        List<JobCareerGraphResponse.VerticalPathNode> vertical = dto.getVerticalPath();
        List<JobCareerGraphResponse.TransitionPath> transitions = dto.getTransitionPaths();

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
            JobCareerGraphResponse.TransitionPath path = transitions.get(i);
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
     * 若 {@link JobCareerGraphResponse#getCurrentNode()} 缺失 level，则取垂直路径中间层级作为起点；
     * 同时在 {@code verticalPath} 中对应节点的 {@code current} 置为 true，便于前端可视化。
     */
    private void markCurrentNode(JobCareerGraphResponse dto) {
        List<JobCareerGraphResponse.VerticalPathNode> vertical = dto.getVerticalPath();
        JobCareerGraphResponse.CurrentNode current = dto.getCurrentNode();

        int currentLevel = (current != null && current.getLevel() != null) ? current.getLevel() : -1;
        if (currentLevel < 0) {
            for (JobCareerGraphResponse.VerticalPathNode node : vertical) {
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

        for (JobCareerGraphResponse.VerticalPathNode node : vertical) {
            node.setCurrent(node.getLevel() != null && node.getLevel() == currentLevel);
        }

        if (current == null) {
            current = new JobCareerGraphResponse.CurrentNode();
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

    /** DTO → Entity（JSONB 列 JSON 文本化） */
    private JobCareerGraph toEntity(JobCareerGraphResponse dto) {
        JobCareerGraph entity = new JobCareerGraph();
        entity.setSummary(dto.getSummary());
        entity.setCurrentNode(JobAnalysisSupport.toJson(
                dto.getCurrentNode(), objectMapper, log, "岗位图谱 JSON 序列化失败，字段将置空"));
        entity.setVerticalPath(JobAnalysisSupport.toJson(
                dto.getVerticalPath(), objectMapper, log, "岗位图谱 JSON 序列化失败，字段将置空"));
        entity.setTransitionPaths(JobAnalysisSupport.toJson(
                dto.getTransitionPaths(), objectMapper, log, "岗位图谱 JSON 序列化失败，字段将置空"));
        return entity;
    }

    /** Entity → DTO（JSONB 列回填为强类型对象） */
    private JobCareerGraphResponse toDto(JobCareerGraph entity) {
        JobCareerGraphResponse dto = new JobCareerGraphResponse();
        dto.setJobId(entity.getJobId());
        dto.setTargetRoleType(StringUtils.hasText(entity.getTargetRoleType()) ? entity.getTargetRoleType()
                : JobRole.fromPromptName(entity.getPromptName()).slug());
        dto.setSummary(entity.getSummary());
        try {
            if (StringUtils.hasText(entity.getCurrentNode())) {
                dto.setCurrentNode(
                        objectMapper.readValue(entity.getCurrentNode(), JobCareerGraphResponse.CurrentNode.class));
            }
            if (StringUtils.hasText(entity.getVerticalPath())) {
                dto.setVerticalPath(objectMapper.readValue(entity.getVerticalPath(),
                        new TypeReference<List<JobCareerGraphResponse.VerticalPathNode>>() {
                        }));
            }
            if (StringUtils.hasText(entity.getTransitionPaths())) {
                dto.setTransitionPaths(objectMapper.readValue(entity.getTransitionPaths(),
                        new TypeReference<List<JobCareerGraphResponse.TransitionPath>>() {
                        }));
            }
        } catch (Exception e) {
            log.warn("反序列化岗位关联图谱 JSON 失败，jobId={}, err={}", entity.getJobId(), e.getMessage());
        }
        return dto;
    }

}
