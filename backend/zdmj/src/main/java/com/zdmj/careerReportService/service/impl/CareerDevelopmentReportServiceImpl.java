package com.zdmj.careerReportService.service.impl;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportCheckDTO;
import com.zdmj.careerReportService.dto.CareerReportDTO;
import com.zdmj.careerReportService.dto.CareerReportGenerateReqDTO;
import com.zdmj.careerReportService.dto.CareerReportPolishReqDTO;
import com.zdmj.careerReportService.dto.CareerReportUpdateReqDTO;
import com.zdmj.careerReportService.entity.CareerDevelopmentReport;
import com.zdmj.careerReportService.mapper.CareerDevelopmentReportMapper;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.prompt.PromptNames;
import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.dto.JobCareerGraphDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.JobCareerGraph;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobCareerGraphService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.matchService.dto.JobStudentMatchDTO;
import com.zdmj.matchService.entity.JobStudentMatch;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 职业发展报告服务实现类
 *
 * <p>负责聚合学生画像、岗位画像、人岗匹配、岗位图谱与知识库向量检索，
 * 调用大模型生成/润色结构化报告，并支持版本化落库、完整性校验与导出。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerDevelopmentReportServiceImpl
        extends ServiceImpl<CareerDevelopmentReportMapper, CareerDevelopmentReport>
        implements CareerDevelopmentReportService {

    /** 状态：草稿 */
    private static final int STATUS_DRAFT = 1;
    /** 状态：已通过完整性校验 */
    private static final int STATUS_CHECKED = 2;
    /** 状态：完整性校验未通过 */
    private static final int STATUS_CHECK_FAILED = 4;

    /** 报告生成时 RAG 检索 Top-K */
    private static final int REPORT_RAG_TOP_K = 8;
    /** 报告生成时 RAG 最低相似度阈值 */
    private static final double REPORT_RAG_MIN_SCORE = 0.35;

    /** 优先命中的知识文档分类（metadata.docCategory） */
    private static final Set<String> CAREER_DOC_CATEGORY_WHITELIST =
            Set.of("learning_path", "career_planning", "industry_trend");

    private final ObjectMapper objectMapper;
    private final ChatUtil chatUtil;
    private final JobService jobService;
    private final JobCapabilityProfileService jobCapabilityProfileService;
    private final JobCareerGraphService jobCareerGraphService;
    private final JobStudentMatchService jobStudentMatchService;
    private final StudentCapabilityProfileService studentCapabilityProfileService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final EmbeddingModel embeddingModel;

    @Override
    public CareerReportDTO getLatestOrNull(Long jobId) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        CareerDevelopmentReport entity = getOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getUserId, userId)
                .eq(CareerDevelopmentReport::getJobId, jobId)
                .eq(CareerDevelopmentReport::getIsLatest, true)
                .orderByDesc(CareerDevelopmentReport::getVersion)
                .last("LIMIT 1"));
        return entity == null ? null : toDto(entity);
    }

    @Override
    public CareerReportDTO generate(Long jobId, CareerReportGenerateReqDTO req) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        // 1. 校验岗位与学生画像前置条件
        JobListItemDTO jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        StudentCapabilityProfileDTO studentProfile = studentCapabilityProfileService.getCurrentUserProfileOrNull();
        if (studentProfile == null) {
            throw new BusinessException(ErrorCode.MATCH_PRECONDITION_MISSING);
        }
        // 2. 加载或生成岗位画像、人岗匹配、岗位图谱
        JobCapabilityProfileDTO jobProfile = loadOrGenerateJobProfile(jobId);
        JobStudentMatchDTO match = loadOrGenerateMatch(jobId);
        JobCareerGraphDTO graph = loadOrGenerateCareerGraph(jobId);
        // 3. 知识库 RAG 检索学习路径片段
        List<KnowledgeRetrivalDTO> ragHits = retrieveLearningPathHits(jobDetail, req);
        // 4. 调用大模型生成结构化报告并做本地完整性校验
        Map<String, Object> reportContent = generateStructuredReport(
                jobDetail, studentProfile, jobProfile, match, graph, ragHits, req);
        CareerReportCheckDTO check = localIntegrityCheck(reportContent);
        // 5. 写入新版本并返回
        JobStudentMatch matchEntity = getOneFromMatchService(userId, jobId);
        JobCareerGraph graphEntity = getOneFromGraphService(jobId);

        CareerDevelopmentReport created = createNewVersion(
                userId,
                jobId,
                matchEntity == null ? null : matchEntity.getId(),
                graphEntity == null ? null : graphEntity.getId(),
                toJson(studentProfile),
                toJson(jobProfile),
                toJson(match),
                toJson(toKnowledgeSources(ragHits)),
                toJson(reportContent),
                toJson(toQualityFlags(check)),
                Boolean.TRUE.equals(check.getPassed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED,
                check.getCompletenessScore(),
                PromptNames.CAREER_REPORT_GENERATE);
        return toDto(created);
    }

    @Override
    public CareerReportDTO polish(Long reportId, CareerReportPolishReqDTO req) {
        CareerDevelopmentReport current = requireOwnedReport(reportId);
        Map<String, Object> currentContent = readMap(current.getReportContent());

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("instruction", req == null ? "" : safe(req.getInstruction()));

        String userMessage = "请对以下职业发展报告进行润色，保持结构不变，增强可执行性与可读性：\n"
                + toJson(currentContent);
        LlmReportPayload payload;
        try {
            payload = chatUtil.chatStructuredOnce(
                    userMessage, PromptNames.CAREER_REPORT_POLISH, vars, LlmReportPayload.class);
        } catch (Exception e) {
            log.error("报告润色失败: reportId={}", reportId, e);
            throw new BusinessException(ErrorCode.CAREER_REPORT_POLISH_FAILED);
        }
        Map<String, Object> polished = payload == null || payload.getReportContent() == null
                ? currentContent : payload.getReportContent();
        CareerReportCheckDTO check = localIntegrityCheck(polished);

        CareerDevelopmentReport created = createNewVersion(
                current.getUserId(),
                current.getJobId(),
                current.getMatchId(),
                current.getCareerGraphId(),
                current.getStudentProfileSnapshot(),
                current.getJobProfileSnapshot(),
                current.getMatchSnapshot(),
                current.getKnowledgeSources(),
                toJson(polished),
                toJson(toQualityFlags(check)),
                Boolean.TRUE.equals(check.getPassed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED,
                check.getCompletenessScore(),
                PromptNames.CAREER_REPORT_POLISH);
        return toDto(created);
    }

    @Override
    public CareerReportCheckDTO checkIntegrity(Long reportId) {
        CareerDevelopmentReport report = requireOwnedReport(reportId);
        Map<String, Object> reportContent = readMap(report.getReportContent());
        CareerReportCheckDTO local = localIntegrityCheck(reportContent);

        try {
            CareerReportCheckDTO llm = chatUtil.chatStructuredOnce(
                    "请检查这份职业发展报告是否完整且可执行：\n" + toJson(reportContent),
                    PromptNames.CAREER_REPORT_INTEGRITY_CHECK,
                    null,
                    CareerReportCheckDTO.class);
            if (llm != null) {
                local = mergeChecks(local, llm);
            }
        } catch (Exception e) {
            log.warn("LLM 完整性检查失败，使用本地校验结果: reportId={}, err={}", reportId, e.getMessage());
        }

        report.setCompletenessScore(local.getCompletenessScore());
        report.setQualityFlags(toJson(toQualityFlags(local)));
        report.setStatus(Boolean.TRUE.equals(local.getPassed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED);
        updateById(report);
        return local;
    }

    @Override
    public CareerReportDTO saveManualEdit(Long reportId, CareerReportUpdateReqDTO req) {
        CareerDevelopmentReport current = requireOwnedReport(reportId);
        if (req == null || req.getReportContent() == null || req.getReportContent().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        CareerReportCheckDTO check = localIntegrityCheck(req.getReportContent());
        CareerDevelopmentReport created = createNewVersion(
                current.getUserId(),
                current.getJobId(),
                current.getMatchId(),
                current.getCareerGraphId(),
                current.getStudentProfileSnapshot(),
                current.getJobProfileSnapshot(),
                current.getMatchSnapshot(),
                current.getKnowledgeSources(),
                toJson(req.getReportContent()),
                toJson(toQualityFlags(check)),
                Boolean.TRUE.equals(check.getPassed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED,
                check.getCompletenessScore(),
                current.getPromptName());
        return toDto(created);
    }

    private JobCapabilityProfileDTO loadOrGenerateJobProfile(Long jobId) {
        JobCapabilityProfileDTO profile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        return profile != null ? profile : jobCapabilityProfileService.getJobCapabilityProfile(jobId);
    }

    private JobStudentMatchDTO loadOrGenerateMatch(Long jobId) {
        JobStudentMatchDTO match = jobStudentMatchService.getOrNull(jobId);
        return match != null ? match : jobStudentMatchService.generate(jobId, null);
    }

    private JobCareerGraphDTO loadOrGenerateCareerGraph(Long jobId) {
        JobCareerGraphDTO graph = jobCareerGraphService.getOrNull(jobId);
        return graph != null ? graph : jobCareerGraphService.generate(jobId);
    }

    /**
     * 从系统知识库检索与岗位/偏好相关的学习路径片段（向量相似度 + 文档分类过滤）。
     */
    private List<KnowledgeRetrivalDTO> retrieveLearningPathHits(JobListItemDTO jobDetail, CareerReportGenerateReqDTO req) {
        Long userId = UserHolder.requireUserId();
        Long knowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();
        String query = buildKnowledgeQuery(jobDetail, req);
        float[] vector = embedQueryText(query);
        if (vector == null) {
            return List.of();
        }
        String vec = knowledgeEmbeddingService.toPgVector(vector);
        List<KnowledgeRetrivalDTO> hits =
                knowledgeVectorMapper.searchBySimilarity(userId, knowledgeId, vec, REPORT_RAG_TOP_K);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<KnowledgeRetrivalDTO> filtered = hits.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= REPORT_RAG_MIN_SCORE)
                .filter(this::isCareerLearningDoc)
                .collect(Collectors.toList());
        return filtered.isEmpty() ? hits.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= REPORT_RAG_MIN_SCORE)
                .collect(Collectors.toList()) : filtered;
    }

    private Map<String, Object> generateStructuredReport(JobListItemDTO jobDetail,
                                                         StudentCapabilityProfileDTO studentProfile,
                                                         JobCapabilityProfileDTO jobProfile,
                                                         JobStudentMatchDTO match,
                                                         JobCareerGraphDTO graph,
                                                         List<KnowledgeRetrivalDTO> ragHits,
                                                         CareerReportGenerateReqDTO req) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("userPreference", req == null ? "" : safe(req.getUserPreference()));
        vars.put("focus", req == null ? "" : safe(req.getFocus()));

        String ragContext = buildRagContext(ragHits);
        StringBuilder sb = new StringBuilder();
        sb.append("请生成结构化职业发展报告，严格输出 JSON。\n\n");
        sb.append("## 岗位信息\n").append(toJson(jobDetail)).append("\n\n");
        sb.append("## 学生画像\n").append(toJson(studentProfile)).append("\n\n");
        sb.append("## 岗位画像\n").append(toJson(jobProfile)).append("\n\n");
        sb.append("## 人岗匹配\n").append(toJson(match)).append("\n\n");
        sb.append("## 岗位图谱\n").append(toJson(graph)).append("\n\n");
        sb.append("## 学习路径知识上下文\n").append(ragContext).append("\n");

        LlmReportPayload payload;
        try {
            payload = chatUtil.chatStructuredOnce(
                    sb.toString(), PromptNames.CAREER_REPORT_GENERATE, vars, LlmReportPayload.class);
        } catch (Exception e) {
            log.error("职业报告生成失败 jobId={}", jobDetail.getId(), e);
            throw new BusinessException(ErrorCode.CAREER_REPORT_GENERATION_FAILED);
        }
        if (payload == null || payload.getReportContent() == null || payload.getReportContent().isEmpty()) {
            throw new BusinessException(ErrorCode.CAREER_REPORT_INVALID);
        }
        return payload.getReportContent();
    }

    /**
     * 加载当前用户拥有的报告，不存在或不属于当前用户时抛业务异常。
     */
    private CareerDevelopmentReport requireOwnedReport(Long reportId) {
        Long userId = UserHolder.requireUserId();
        if (reportId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        CareerDevelopmentReport report = getOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getId, reportId)
                .eq(CareerDevelopmentReport::getUserId, userId));
        if (report == null) {
            throw new BusinessException(ErrorCode.CAREER_REPORT_NOT_FOUND);
        }
        return report;
    }

    /**
     * 将同用户+岗位下的旧 latest 置为 false，并插入递增版本的新记录。
     */
    private CareerDevelopmentReport createNewVersion(Long userId,
                                                     Long jobId,
                                                     Long matchId,
                                                     Long careerGraphId,
                                                     String studentSnapshot,
                                                     String jobSnapshot,
                                                     String matchSnapshot,
                                                     String knowledgeSources,
                                                     String reportContent,
                                                     String qualityFlags,
                                                     Integer status,
                                                     Integer completenessScore,
                                                     String promptName) {
        CareerDevelopmentReport latest = getOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getUserId, userId)
                .eq(CareerDevelopmentReport::getJobId, jobId)
                .eq(CareerDevelopmentReport::getIsLatest, true)
                .orderByDesc(CareerDevelopmentReport::getVersion)
                .last("LIMIT 1"));
        int nextVersion = 1;
        if (latest != null) {
            nextVersion = latest.getVersion() == null ? 1 : latest.getVersion() + 1;
            latest.setIsLatest(false);
            updateById(latest);
        }

        CareerDevelopmentReport entity = new CareerDevelopmentReport();
        entity.setUserId(userId);
        entity.setJobId(jobId);
        entity.setMatchId(matchId);
        entity.setCareerGraphId(careerGraphId);
        entity.setStudentProfileSnapshot(studentSnapshot);
        entity.setJobProfileSnapshot(jobSnapshot);
        entity.setMatchSnapshot(matchSnapshot);
        entity.setKnowledgeSources(knowledgeSources);
        entity.setReportContent(reportContent);
        entity.setQualityFlags(qualityFlags);
        entity.setStatus(status);
        entity.setCompletenessScore(completenessScore);
        entity.setVersion(nextVersion);
        entity.setIsLatest(true);
        entity.setPromptName(promptName);
        save(entity);
        return entity;
    }

    private JobStudentMatch getOneFromMatchService(Long userId, Long jobId) {
        return jobStudentMatchService.getOne(new LambdaQueryWrapper<JobStudentMatch>()
                .eq(JobStudentMatch::getUserId, userId)
                .eq(JobStudentMatch::getJobId, jobId)
                .last("LIMIT 1"));
    }

    private JobCareerGraph getOneFromGraphService(Long jobId) {
        return jobCareerGraphService.getOne(new LambdaQueryWrapper<JobCareerGraph>()
                .eq(JobCareerGraph::getJobId, jobId)
                .last("LIMIT 1"));
    }

    private CareerReportDTO toDto(CareerDevelopmentReport entity) {
        CareerReportDTO dto = new CareerReportDTO();
        dto.setId(entity.getId());
        dto.setJobId(entity.getJobId());
        dto.setStatus(entity.getStatus());
        dto.setCompletenessScore(entity.getCompletenessScore());
        dto.setVersion(entity.getVersion());
        dto.setLatest(entity.getIsLatest());
        dto.setPromptName(entity.getPromptName());
        dto.setReportContent(readMap(entity.getReportContent()));
        dto.setQualityFlags(readMap(entity.getQualityFlags()));
        dto.setKnowledgeSources(readListOfMap(entity.getKnowledgeSources()));
        return dto;
    }

    private float[] embedQueryText(String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return null;
        }
        try {
            DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                    .textType(DashScopeModel.EmbeddingTextType.QUERY.getValue())
                    .build();
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(queryText), options));
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return null;
            }
            return response.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.warn("职业报告检索向量生成失败：{}", e.getMessage());
            return null;
        }
    }

    private String buildKnowledgeQuery(JobListItemDTO jobDetail, CareerReportGenerateReqDTO req) {
        StringBuilder sb = new StringBuilder();
        sb.append("生成职业发展学习路径 ");
        sb.append(safe(jobDetail.getJobName())).append(" ");
        sb.append(safe(jobDetail.getDescription())).append(" ");
        if (req != null) {
            sb.append(safe(req.getFocus())).append(" ");
            sb.append(safe(req.getUserPreference()));
        }
        return sb.toString();
    }

    private boolean isCareerLearningDoc(KnowledgeRetrivalDTO dto) {
        if (dto == null || dto.getMetadata() == null || dto.getMetadata().isEmpty()) {
            return false;
        }
        Object category = dto.getMetadata().get("docCategory");
        if (category == null) {
            category = dto.getMetadata().get("category");
        }
        if (category == null) {
            return false;
        }
        return CAREER_DOC_CATEGORY_WHITELIST.contains(
                String.valueOf(category).trim().toLowerCase(Locale.ROOT));
    }

    private String buildRagContext(List<KnowledgeRetrivalDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return "未命中学习路径文档，请仅基于画像和匹配结果生成。";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (KnowledgeRetrivalDTO hit : hits) {
            sb.append("### 片段 ").append(idx++).append('\n');
            sb.append("- documentId: ").append(hit.getDocumentId()).append('\n');
            sb.append("- score: ").append(hit.getScore()).append('\n');
            sb.append("- metadata: ").append(hit.getMetadata()).append('\n');
            sb.append("- content: ").append(safe(hit.getContent())).append('\n');
        }
        return sb.toString();
    }

    private List<Map<String, Object>> toKnowledgeSources(List<KnowledgeRetrivalDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeRetrivalDTO hit : hits) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentId", hit.getDocumentId());
            row.put("chunkIndex", hit.getChunkIndex());
            row.put("score", hit.getScore());
            row.put("metadata", hit.getMetadata() == null ? Map.of() : hit.getMetadata());
            out.add(row);
        }
        return out;
    }

    /**
     * 本地规则校验报告必备章节与行动计划可执行性（不调用大模型）。
     */
    private CareerReportCheckDTO localIntegrityCheck(Map<String, Object> reportContent) {
        CareerReportCheckDTO dto = new CareerReportCheckDTO();
        List<String> missingSections = new ArrayList<>();
        List<String> nonActionableItems = new ArrayList<>();
        List<String> weakEvidenceItems = new ArrayList<>();
        if (reportContent == null) {
            reportContent = Map.of();
        }
        requireSection(reportContent, "careerExploration", "职业探索", missingSections);
        requireSection(reportContent, "careerGoals", "职业目标", missingSections);
        requireSection(reportContent, "careerPath", "职业路径", missingSections);
        requireSection(reportContent, "actionPlan", "行动计划", missingSections);
        requireSection(reportContent, "evaluationPlan", "评估计划", missingSections);

        Object actionPlan = reportContent.get("actionPlan");
        if (actionPlan instanceof Map<?, ?> map) {
            checkActionBucket(map.get("shortTerm"), "shortTerm", nonActionableItems);
            checkActionBucket(map.get("midTerm"), "midTerm", nonActionableItems);
        } else {
            nonActionableItems.add("actionPlan");
        }

        Object evidence = reportContent.get("evidence");
        if (!(evidence instanceof Map<?, ?> || evidence instanceof List<?>)) {
            weakEvidenceItems.add("evidence");
        }

        int score = 100;
        score -= missingSections.size() * 12;
        score -= nonActionableItems.size() * 8;
        score -= weakEvidenceItems.size() * 6;
        score = Math.max(0, Math.min(100, score));

        dto.setMissingSections(distinct(missingSections));
        dto.setNonActionableItems(distinct(nonActionableItems));
        dto.setWeakEvidenceItems(distinct(weakEvidenceItems));
        dto.setCompletenessScore(score);
        dto.setPassed(missingSections.isEmpty() && nonActionableItems.size() <= 1);
        dto.setRiskLevel(score >= 80 ? "low" : score >= 60 ? "medium" : "high");
        return dto;
    }

    private CareerReportCheckDTO mergeChecks(CareerReportCheckDTO local, CareerReportCheckDTO llm) {
        CareerReportCheckDTO merged = new CareerReportCheckDTO();
        merged.setMissingSections(distinct(mergeList(local.getMissingSections(), llm.getMissingSections())));
        merged.setNonActionableItems(distinct(mergeList(local.getNonActionableItems(), llm.getNonActionableItems())));
        merged.setWeakEvidenceItems(distinct(mergeList(local.getWeakEvidenceItems(), llm.getWeakEvidenceItems())));
        int l = local.getCompletenessScore() == null ? 0 : local.getCompletenessScore();
        int r = llm.getCompletenessScore() == null ? l : llm.getCompletenessScore();
        merged.setCompletenessScore(Math.max(0, Math.min(100, (l + r) / 2)));
        boolean passed = !Boolean.FALSE.equals(local.getPassed()) && !Boolean.FALSE.equals(llm.getPassed());
        merged.setPassed(passed);
        merged.setRiskLevel(merged.getCompletenessScore() >= 80 ? "low"
                : merged.getCompletenessScore() >= 60 ? "medium" : "high");
        return merged;
    }

    private Map<String, Object> toQualityFlags(CareerReportCheckDTO check) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("missingSections", check.getMissingSections() == null ? List.of() : check.getMissingSections());
        map.put("nonActionableItems", check.getNonActionableItems() == null ? List.of() : check.getNonActionableItems());
        map.put("weakEvidenceItems", check.getWeakEvidenceItems() == null ? List.of() : check.getWeakEvidenceItems());
        map.put("riskLevel", check.getRiskLevel());
        return map;
    }

    private static void requireSection(Map<String, Object> reportContent, String key,
                                       String displayName, List<String> missingSections) {
        Object v = reportContent.get(key);
        if (v == null) {
            missingSections.add(displayName);
            return;
        }
        if (v instanceof String s && !StringUtils.hasText(s)) {
            missingSections.add(displayName);
        }
        if (v instanceof List<?> list && list.isEmpty()) {
            missingSections.add(displayName);
        }
        if (v instanceof Map<?, ?> map && map.isEmpty()) {
            missingSections.add(displayName);
        }
    }

    private static void checkActionBucket(Object bucket, String bucketName, List<String> nonActionableItems) {
        if (bucket == null) {
            nonActionableItems.add(bucketName);
            return;
        }
        if (bucket instanceof List<?> list) {
            if (list.isEmpty()) {
                nonActionableItems.add(bucketName);
                return;
            }
            int idx = 0;
            for (Object item : list) {
                idx++;
                if (!(item instanceof Map<?, ?> map)) {
                    nonActionableItems.add(bucketName + "#" + idx);
                    continue;
                }
                if (!hasAnyKey(map, "cycle", "period", "timeWindow")) {
                    nonActionableItems.add(bucketName + "#" + idx + ":missingCycle");
                }
                if (!hasAnyKey(map, "deliverable", "milestone", "output")) {
                    nonActionableItems.add(bucketName + "#" + idx + ":missingDeliverable");
                }
            }
            return;
        }
        if (bucket instanceof Map<?, ?> map) {
            if (!hasAnyKey(map, "tasks", "items")) {
                nonActionableItems.add(bucketName + ":missingTasks");
            }
            return;
        }
        nonActionableItems.add(bucketName);
    }

    private static boolean hasAnyKey(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v == null) {
                continue;
            }
            if (v instanceof String s && StringUtils.hasText(s)) {
                return true;
            }
            if (v instanceof List<?> list && !list.isEmpty()) {
                return true;
            }
            if (v instanceof Map<?, ?> m && !m.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> readMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> readListOfMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object val) {
        if (val == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static List<String> mergeList(List<String> left, List<String> right) {
        List<String> out = new ArrayList<>();
        if (left != null) {
            out.addAll(left);
        }
        if (right != null) {
            out.addAll(right);
        }
        return out;
    }

    private static List<String> distinct(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(input.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList())));
    }

    /** 大模型结构化输出：报告正文 */
    @Data
    private static class LlmReportPayload {
        private Map<String, Object> reportContent;
    }
}
