package com.zdmj.careerReportService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.careerReportService.dto.CareerReportResponse;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.dto.CareerReportPolishRequest;
import com.zdmj.careerReportService.dto.CareerReportUpdateRequest;
import com.zdmj.careerReportService.entity.CareerDevelopmentReport;
import com.zdmj.careerReportService.mapper.CareerDevelopmentReportMapper;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.careerReportService.support.ReportIntegrity;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.knowledgeService.dto.KnowledgeRetrivalDTO;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 职业发展报告服务实现类
 *
 * <p>负责聚合学生画像、岗位画像、人岗匹配与知识库向量检索，
 * 调用大模型生成或润色结构化报告。模型调用在事务外完成，覆盖写入在同一次短事务中完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerDevelopmentReportServiceImpl
        extends ServiceImpl<CareerDevelopmentReportMapper, CareerDevelopmentReport>
        implements CareerDevelopmentReportService {

    private static final String GENERATE_PROMPT = "career-report/generate";
    private static final String POLISH_PROMPT = "career-report/polish";
    private static final String INTEGRITY_CHECK_PROMPT = "career-report/integrity-check";

    /** 状态：已通过完整性校验 */
    private static final int STATUS_CHECKED = 2;
    /** 状态：完整性校验未通过 */
    private static final int STATUS_CHECK_FAILED = 4;

    /** 报告生成时 RAG 检索 Top-K（合并多库后） */
    private static final int REPORT_RAG_TOP_K = 8;
    /** 报告生成时 RAG 最低相似度阈值 */
    private static final double REPORT_RAG_MIN_SCORE = 0.35;

    /** 优先命中的知识文档分类（metadata.docCategory） */
    private static final Set<String> CAREER_DOC_CATEGORY_WHITELIST =
            Set.of("learning_path", "career_planning", "industry_trend");

    private static final DateTimeFormatter REPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> SOURCES_TYPE = new TypeReference<>() {};

    private static final Pattern MARKDOWN_H1 = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final Pattern PLACEHOLDER_DOC_TITLE = Pattern.compile("^文档 #\\d+$");

    private final ObjectMapper objectMapper;
    private final ModelGateway modelGateway;
    private final JobService jobService;
    private final JobCapabilityProfileService jobCapabilityProfileService;
    private final JobStudentMatchService jobStudentMatchService;
    private final StudentCapabilityProfileService studentCapabilityProfileService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final EmbeddingGateway embeddingGateway;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public CareerReportResponse getLatestOrNull(Long jobId) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "jobId不能为空");
        }
        CareerDevelopmentReport entity = getOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getUserId, userId)
                .eq(CareerDevelopmentReport::getJobId, jobId)
                .last("LIMIT 1"));
        return entity == null ? null : toResponse(entity);
    }

    @Override
    public CareerReportResponse generate(Long jobId, CareerReportGenerateRequest req) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "jobId不能为空");
        }
        // 1. 校验岗位与学生画像前置条件
        JobListItemResponse jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        StudentCapabilityProfileResponse studentProfile = studentCapabilityProfileService.getCurrentUserProfileOrNull();
        if (studentProfile == null) {
            throw new BusinessException(ErrorCode.MATCH_PRECONDITION_MISSING);
        }
        // 2. 加载或生成岗位画像、人岗匹配
        JobCapabilityProfileResponse jobProfile = loadOrGenerateJobProfile(jobId);
        JobStudentMatchResponse match = loadOrGenerateMatch(jobId);
        // 3. 知识库 RAG 检索学习路径片段
        List<KnowledgeRetrivalDTO> ragHits = retrieveLearningPathHits(jobDetail, req);
        // 4. 调用大模型生成结构化报告
        Map<String, Object> reportContent = generateStructuredReport(
                userId, jobDetail, studentProfile, jobProfile, match, ragHits, req);
        // 5. 事务外组装快照并校验正文，再覆盖写入唯一一行
        CareerDevelopmentReport draft = new CareerDevelopmentReport();
        draft.setUserId(userId);
        draft.setJobId(jobId);
        draft.setMatchId(match.getId());
        draft.setStudentProfileSnapshot(toJson(studentProfile));
        draft.setJobProfileSnapshot(toJson(jobProfile));
        draft.setMatchSnapshot(toJson(match));
        draft.setKnowledgeSources(toJson(toKnowledgeSources(ragHits)));
        return toResponse(saveReport(draft, reportContent, GENERATE_PROMPT));
    }

    @Override
    public CareerReportResponse polish(Long reportId, CareerReportPolishRequest req) {
        CareerDevelopmentReport current = requireOwnedReport(reportId);
        Map<String, Object> currentContent = readJson(current.getReportContent(), MAP_TYPE, Map.of());

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("instruction", req == null ? "" : safe(req.getInstruction()));
        String currentDate = reportContextDate();
        vars.put("currentDate", currentDate);

        String userMessage = "请对以下职业发展报告进行润色，保持结构不变，增强可执行性与可读性：\n"
                + "## 当前日期（北京时间）\n" + currentDate + "\n\n"
                + "若调整 evaluationPlan 中 quantitativeMetrics 的 deadline，具体日期（YYYY-MM-DD）不得早于当前日期。\n\n"
                + toJson(currentContent);
        Map<String, Object> polished = requestReportContent(current.getUserId(), userMessage,
                POLISH_PROMPT, vars, ErrorCode.CAREER_REPORT_POLISH_FAILED);
        return toResponse(saveReport(draftFromCurrent(current), polished, POLISH_PROMPT));
    }

    @Override
    public CareerReportCheckResponse checkIntegrity(Long reportId) {
        CareerDevelopmentReport report = requireOwnedReport(reportId);
        Map<String, Object> reportContent = readJson(report.getReportContent(), MAP_TYPE, Map.of());
        CareerReportCheckResponse local = ReportIntegrity.check(reportContent);

        CareerReportCheckResponse modelCheck = modelGateway.generateStructured(CurrentActor.of(report.getUserId()),
                new ModelRequest("请检查这份职业发展报告是否完整且可执行：\n" + toJson(reportContent),
                        INTEGRITY_CHECK_PROMPT, null), CareerReportCheckResponse.class);
        CareerReportCheckResponse check = ReportIntegrity.merge(local, modelCheck);
        Integer completenessScore = check.getCompletenessScore();
        String qualityFlags = toJson(ReportIntegrity.qualityFlags(check));
        Integer status = Boolean.TRUE.equals(check.getPassed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED;
        transactionTemplate.executeWithoutResult(tx -> writeIntegrityResult(
                report.getId(), report.getUserId(), report.getJobId(),
                completenessScore, qualityFlags, status));
        return check;
    }

    @Override
    public CareerReportResponse saveManualEdit(Long reportId, CareerReportUpdateRequest req) {
        CareerDevelopmentReport current = requireOwnedReport(reportId);
        if (req == null || req.getReportContent() == null || req.getReportContent().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "报告内容不能为空");
        }
        return toResponse(saveReport(draftFromCurrent(current), req.getReportContent(), current.getPromptName()));
    }

    private JobCapabilityProfileResponse loadOrGenerateJobProfile(Long jobId) {
        JobCapabilityProfileResponse profile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        return profile != null ? profile : jobCapabilityProfileService.getJobCapabilityProfile(jobId);
    }

    private JobStudentMatchResponse loadOrGenerateMatch(Long jobId) {
        JobStudentMatchResponse match = jobStudentMatchService.getOrNull(jobId);
        return match != null ? match : jobStudentMatchService.generate(jobId, null);
    }

    /**
     * 从用户私有库（scope=1）与学习路线库（scope=3）检索相关片段（向量相似度 + 文档分类过滤）。
     */
    private List<KnowledgeRetrivalDTO> retrieveLearningPathHits(JobListItemResponse jobDetail, CareerReportGenerateRequest req) {
        Long userId = UserHolder.requireUserId();
        String query = buildKnowledgeQuery(jobDetail, req);
        float[] vector = embedQueryText(userId, query);
        String vec = knowledgeEmbeddingService.toPgVector(vector);

        List<KnowledgeRetrivalDTO> merged = new ArrayList<>();
        merged.addAll(searchKnowledgeVectors(userId, knowledgeBasesService.getOrCreateKnowledgeBaseId(), vec));

        Long learningPathKbId = knowledgeBasesService.findKnowledgeBaseIdByScope(
                KnowledgeScopeEnum.LEARNING_PATH.getCode());
        if (learningPathKbId != null) {
            merged.addAll(searchKnowledgeVectors(
                    KnowledgeScopeEnum.SYSTEM_OWNER_USER_ID, learningPathKbId, vec));
        } else {
            log.debug("未找到 scope=3 学习路线知识库，报告 RAG 仅检索用户私有库");
        }

        if (merged.isEmpty()) {
            return List.of();
        }

        List<KnowledgeRetrivalDTO> hits = rankAndLimitHits(merged, REPORT_RAG_TOP_K);
        List<KnowledgeRetrivalDTO> qualified = hits.stream()
                .filter(hit -> hit.getScore() >= REPORT_RAG_MIN_SCORE).toList();
        List<KnowledgeRetrivalDTO> preferred = qualified.stream().filter(this::isCareerLearningDoc).toList();
        return preferred.isEmpty() ? qualified : preferred;
    }

    private List<KnowledgeRetrivalDTO> searchKnowledgeVectors(Long ownerUserId, Long knowledgeId, String queryEmbedding) {
        if (knowledgeId == null) {
            return List.of();
        }
        List<KnowledgeRetrivalDTO> hits = knowledgeVectorMapper.searchBySimilarity(
                ownerUserId, knowledgeId, queryEmbedding, REPORT_RAG_TOP_K);
        return hits == null ? List.of() : hits;
    }

    private List<KnowledgeRetrivalDTO> rankAndLimitHits(List<KnowledgeRetrivalDTO> hits, int topK) {
        Map<String, KnowledgeRetrivalDTO> deduped = new LinkedHashMap<>();
        hits.stream()
                .filter(h -> h.getScore() != null)
                .sorted(Comparator.comparing(KnowledgeRetrivalDTO::getScore).reversed())
                .forEach(h -> {
                    String key = Objects.toString(h.getDocumentId(), "") + ":"
                            + Objects.toString(h.getChunkIndex(), "");
                    deduped.putIfAbsent(key, h);
                });
        return deduped.values().stream().limit(topK).collect(Collectors.toList());
    }

    private Map<String, Object> generateStructuredReport(Long userId,
                                                         JobListItemResponse jobDetail,
                                                         StudentCapabilityProfileResponse studentProfile,
                                                         JobCapabilityProfileResponse jobProfile,
                                                         JobStudentMatchResponse match,
                                                         List<KnowledgeRetrivalDTO> ragHits,
                                                         CareerReportGenerateRequest req) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("userPreference", req == null ? "" : safe(req.getUserPreference()));
        vars.put("focus", req == null ? "" : safe(req.getFocus()));
        String currentDate = reportContextDate();
        vars.put("currentDate", currentDate);

        String ragContext = buildRagContext(ragHits);
        StringBuilder sb = new StringBuilder();
        sb.append("请生成结构化职业发展报告，严格输出 JSON。\n\n");
        sb.append("## 当前日期（北京时间）\n").append(currentDate).append("\n\n");
        sb.append("制定 evaluationPlan.quantitativeMetrics 的 deadline 时，必须以当前日期为基准：")
                .append("若使用 YYYY-MM-DD，不得早于 ").append(currentDate)
                .append("；短期建议 1~3 个月内，中期 3~6 个月内。\n\n");
        sb.append("## 岗位信息\n").append(toJson(jobDetail)).append("\n\n");
        sb.append("## 学生画像\n").append(toJson(studentProfile)).append("\n\n");
        sb.append("## 岗位画像\n").append(toJson(jobProfile)).append("\n\n");
        sb.append("## 人岗匹配\n").append(toJson(match)).append("\n\n");
        sb.append("## 学习路径知识上下文\n").append(ragContext).append("\n");

        return requestReportContent(userId, sb.toString(), GENERATE_PROMPT, vars,
                ErrorCode.CAREER_REPORT_GENERATION_FAILED);
    }

    /**
     * 调用报告生成或润色模型，并校验返回正文。调用失败和空正文均终止保存。
     *
     * @param userId 当前用户编号
     * @param message 已组装的业务输入
     * @param prompt 提示词名称
     * @param variables 提示词变量
     * @param failure 模型调用失败对应的业务错误码
     * @return 非空报告正文
     * @throws BusinessException 模型调用失败或正文缺失
     */
    private Map<String, Object> requestReportContent(Long userId, String message, String prompt,
            Map<String, Object> variables, ErrorCode failure) {
        LlmReportPayload payload;
        try {
            payload = modelGateway.generateStructured(CurrentActor.of(userId),
                    new ModelRequest(message, prompt, variables), LlmReportPayload.class);
        } catch (RuntimeException exception) {
            throw new BusinessException(failure, exception);
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reportId不能为空");
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
     * 用当前报告的快照复制出待覆盖的报告。编号由覆盖写入保留。
     *
     * @param current 当前用户拥有的报告
     * @return 尚未写入的报告
     */
    private CareerDevelopmentReport draftFromCurrent(CareerDevelopmentReport current) {
        CareerDevelopmentReport draft = new CareerDevelopmentReport();
        draft.setUserId(current.getUserId());
        draft.setJobId(current.getJobId());
        draft.setMatchId(current.getMatchId());
        draft.setStudentProfileSnapshot(current.getStudentProfileSnapshot());
        draft.setJobProfileSnapshot(current.getJobProfileSnapshot());
        draft.setMatchSnapshot(current.getMatchSnapshot());
        draft.setKnowledgeSources(current.getKnowledgeSources());
        return draft;
    }

    /**
     * 报告写入的唯一入口。事务外校验正文、组装质量标记及 JSON，短事务内插入或覆盖同一用户与岗位的唯一一行。
     *
     * @param draft 待写入报告，包含用户、岗位及已有快照
     * @param content 报告正文
     * @param promptName 本次提示词名称
     * @return 已写入的报告，编号在覆盖时保持不变
     */
    private CareerDevelopmentReport saveReport(CareerDevelopmentReport draft, Map<String, Object> content,
            String promptName) {
        if (draft.getUserId() == null || draft.getJobId() == null || !StringUtils.hasText(promptName)) {
            throw new IllegalStateException("报告缺少用户、岗位或提示词");
        }
        CareerReportCheckResponse check = ReportIntegrity.check(content);
        draft.setReportContent(toJson(content));
        draft.setQualityFlags(toJson(ReportIntegrity.qualityFlags(check)));
        draft.setStatus(Boolean.TRUE.equals(check.getPassed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED);
        draft.setCompletenessScore(check.getCompletenessScore());
        draft.setPromptName(promptName);
        CareerDevelopmentReport saved = transactionTemplate.execute(status -> overwriteReport(draft));
        if (saved == null) {
            throw new IllegalStateException("职业发展报告未写入");
        }
        return saved;
    }

    /**
     * 在当前事务中锁定用户与岗位，没有记录则插入，已有记录则按原编号覆盖。写入行数必须为 1，否则终止并由事务回滚。
     *
     * @param draft 待写入报告
     * @return 已写入的报告
     */
    private CareerDevelopmentReport overwriteReport(CareerDevelopmentReport draft) {
        lockUserJob(draft.getUserId(), draft.getJobId());
        CareerDevelopmentReport existing = baseMapper.selectOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getUserId, draft.getUserId())
                .eq(CareerDevelopmentReport::getJobId, draft.getJobId())
                .last("FOR UPDATE"));
        if (existing == null) {
            draft.setId(null);
            int inserted = baseMapper.insert(draft);
            if (inserted != 1 || draft.getId() == null) {
                throw new IllegalStateException("职业发展报告未写入");
            }
            return draft;
        }
        if (existing.getId() == null) {
            throw new IllegalStateException("职业发展报告未写入");
        }
        draft.setId(existing.getId());
        int updated = baseMapper.updateById(draft);
        if (updated != 1) {
            throw new IllegalStateException("职业发展报告未写入");
        }
        return draft;
    }

    /**
     * 在短事务中按用户与岗位加锁后，只更新指定报告的完整度、质量标记和状态。
     *
     * @param reportId 报告编号
     * @param userId 用户编号
     * @param jobId 岗位编号
     * @param completenessScore 完整度
     * @param qualityFlags 质量标记 JSON
     * @param status 报告状态
     */
    private void writeIntegrityResult(Long reportId, Long userId, Long jobId, Integer completenessScore,
                                      String qualityFlags, Integer status) {
        lockUserJob(userId, jobId);
        CareerDevelopmentReport current = baseMapper.selectOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getId, reportId)
                .eq(CareerDevelopmentReport::getUserId, userId)
                .last("FOR UPDATE"));
        if (current == null) {
            throw new BusinessException(ErrorCode.CAREER_REPORT_NOT_FOUND);
        }
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getId, reportId)
                .eq(CareerDevelopmentReport::getUserId, userId)
                .set(CareerDevelopmentReport::getCompletenessScore, completenessScore)
                .set(CareerDevelopmentReport::getQualityFlags, qualityFlags)
                .set(CareerDevelopmentReport::getStatus, status));
        if (updated != 1) {
            throw new IllegalStateException("完整性检查结果写入失败");
        }
    }

    /**
     * 按用户与岗位取得事务级咨询锁。同一对用户和岗位的报告写入会排队，事务结束时释放。
     *
     * @param userId 用户编号
     * @param jobId 岗位编号
     */
    private void lockUserJob(Long userId, Long jobId) {
        String lockKey = "career-development-report:" + userId + ":" + jobId;
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, lockKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("报告写入锁未取得");
                    }
                }
            }
            return null;
        });
    }

    private CareerReportResponse toResponse(CareerDevelopmentReport entity) {
        CareerReportResponse response = new CareerReportResponse();
        response.setId(entity.getId());
        response.setJobId(entity.getJobId());
        response.setStatus(entity.getStatus());
        response.setCompletenessScore(entity.getCompletenessScore());
        response.setPromptName(entity.getPromptName());
        response.setReportContent(readJson(entity.getReportContent(), MAP_TYPE, Map.of()));
        response.setQualityFlags(readJson(entity.getQualityFlags(), MAP_TYPE, Map.of()));
        response.setKnowledgeSources(enrichKnowledgeSources(readJson(entity.getKnowledgeSources(), SOURCES_TYPE, List.of())));
        return response;
    }

    /**
     * 为报告检索生成查询向量。嵌入失败时保留原因并终止报告生成。
     *
     * @param userId 当前用户
     * @param queryText 检索文本
     * @return 有效的查询向量
     */
    private float[] embedQueryText(Long userId, String queryText) {
        List<float[]> vectors;
        try {
            vectors = embeddingGateway.embed(CurrentActor.of(userId), List.of(queryText));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("报告查询向量生成失败", exception);
            throw new BusinessException(ErrorCode.CAREER_REPORT_GENERATION_FAILED, exception);
        }
        if (vectors == null || vectors.isEmpty() || vectors.get(0) == null || vectors.get(0).length == 0) {
            throw new BusinessException(ErrorCode.CAREER_REPORT_GENERATION_FAILED, "查询向量生成失败");
        }
        return vectors.get(0);
    }

    private String buildKnowledgeQuery(JobListItemResponse jobDetail, CareerReportGenerateRequest req) {
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
        if (dto == null) {
            return false;
        }
        String category = resolveDocCategory(dto);
        if (!StringUtils.hasText(category)) {
            return false;
        }
        return CAREER_DOC_CATEGORY_WHITELIST.contains(category.trim().toLowerCase(Locale.ROOT));
    }

    private String resolveDocCategory(KnowledgeRetrivalDTO dto) {
        if (StringUtils.hasText(dto.getDocCategory())) {
            return dto.getDocCategory();
        }
        if (dto.getMetadata() == null || dto.getMetadata().isEmpty()) {
            return null;
        }
        Object category = dto.getMetadata().get("docCategory");
        if (category == null) {
            category = dto.getMetadata().get("category");
        }
        return category == null ? null : String.valueOf(category);
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
        Map<Long, KnowledgeDocument> docById = loadDocumentsByIds(hits.stream()
                .map(KnowledgeRetrivalDTO::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeRetrivalDTO hit : hits) {
            KnowledgeDocument doc = hit.getDocumentId() == null ? null : docById.get(hit.getDocumentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentId", hit.getDocumentId());
            row.put("chunkIndex", hit.getChunkIndex());
            row.put("score", hit.getScore());
            row.put("title", knowledgeSourceTitle(doc, hit.getMetadata(), hit.getContent(), hit.getDocumentId()));
            row.put("snippet", buildKnowledgeSourceSnippet(hit));
            row.put("metadata", mergeSourceMetadata(hit, doc));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> enrichKnowledgeSources(List<Map<String, Object>> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<Long> docIds = sources.stream()
                .filter(this::needsKnowledgeSourceTitleEnrich)
                .map(row -> toLong(row.get("documentId")))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, KnowledgeDocument> docById = loadDocumentsByIds(docIds);
        for (Map<String, Object> row : sources) {
            if (!needsKnowledgeSourceTitleEnrich(row)) {
                continue;
            }
            Long docId = toLong(row.get("documentId"));
            KnowledgeDocument doc = docId == null ? null : docById.get(docId);
            Object metadata = row.get("metadata");
            Object snippet = row.get("snippet");
            row.put("title", knowledgeSourceTitle(
                    doc,
                    metadata instanceof Map<?, ?> meta ? meta : null,
                    snippet == null ? null : String.valueOf(snippet),
                    docId));
        }
        return sources;
    }

    private boolean needsKnowledgeSourceTitleEnrich(Map<String, Object> row) {
        Object title = row.get("title");
        if (title == null || !StringUtils.hasText(String.valueOf(title))) {
            return true;
        }
        return PLACEHOLDER_DOC_TITLE.matcher(String.valueOf(title).trim()).matches();
    }

    /**
     * 解析知识来源标题。依次使用文档标题、元数据中的文件名、正文里的一级标题；都没有时用「学习路线」加文档编号。
     *
     * @param doc 知识文档，可以为空
     * @param metadata 来源元数据，可以为空
     * @param headingText 可能包含一级标题的正文或片段，可以为空
     * @param documentId 文档编号，可以为空
     * @return 展示标题
     */
    private String knowledgeSourceTitle(KnowledgeDocument doc, Map<?, ?> metadata, String headingText,
                                        Long documentId) {
        String fromDoc = documentDisplayTitle(doc);
        if (StringUtils.hasText(fromDoc)) {
            return fromDoc;
        }
        String fromMeta = titleFromMetadataMap(metadata);
        if (StringUtils.hasText(fromMeta)) {
            return fromMeta;
        }
        String fromHeading = parseMarkdownHeadingTitle(headingText);
        if (StringUtils.hasText(fromHeading)) {
            return fromHeading;
        }
        return documentId != null ? "学习路线 #" + documentId : "知识片段";
    }

    private Map<Long, KnowledgeDocument> loadDocumentsByIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectBatchIds(documentIds);
        if (docs == null || docs.isEmpty()) {
            return Map.of();
        }
        return docs.stream()
                .filter(doc -> doc.getId() != null)
                .collect(Collectors.toMap(KnowledgeDocument::getId, doc -> doc, (a, b) -> a));
    }

    private static String documentDisplayTitle(KnowledgeDocument doc) {
        if (doc == null) {
            return null;
        }
        if (StringUtils.hasText(doc.getTitle())) {
            return doc.getTitle().trim();
        }
        if (StringUtils.hasText(doc.getContent())) {
            return filenameToLearningRouteTitle(doc.getContent());
        }
        return titleFromMetadataMap(doc.getMetadata());
    }

    private static String titleFromMetadataMap(Map<?, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object sourceFile = metadata.get("sourceFile");
        if (sourceFile != null && StringUtils.hasText(String.valueOf(sourceFile))) {
            return filenameToLearningRouteTitle(String.valueOf(sourceFile));
        }
        Object content = metadata.get("content");
        if (content != null && StringUtils.hasText(String.valueOf(content))) {
            return filenameToLearningRouteTitle(String.valueOf(content));
        }
        return null;
    }

    private static Map<String, Object> mergeSourceMetadata(KnowledgeRetrivalDTO hit, KnowledgeDocument doc) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (doc != null && doc.getMetadata() != null) {
            merged.putAll(doc.getMetadata());
        }
        if (hit.getMetadata() != null) {
            merged.putAll(hit.getMetadata());
        }
        if (doc != null && StringUtils.hasText(doc.getTitle())) {
            merged.putIfAbsent("documentTitle", doc.getTitle());
        }
        if (doc != null && StringUtils.hasText(doc.getContent())) {
            merged.putIfAbsent("sourceFile", doc.getContent());
        }
        return merged;
    }

    private static String filenameToLearningRouteTitle(String file) {
        String name = file == null ? "" : file.trim();
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static String parseMarkdownHeadingTitle(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = MARKDOWN_H1.matcher(text.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int hashIdx = text.indexOf("# ");
        if (hashIdx >= 0) {
            String tail = text.substring(hashIdx + 2);
            int end = tail.indexOf(" ## ");
            if (end < 0) {
                end = tail.indexOf('·');
            }
            if (end < 0) {
                end = Math.min(tail.length(), 40);
            }
            return tail.substring(0, end).trim();
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildKnowledgeSourceSnippet(KnowledgeRetrivalDTO hit) {
        List<String> parts = new ArrayList<>();
        if (hit.getScore() != null) {
            parts.add(String.format(Locale.ROOT, "相似度 %.1f%%", hit.getScore() * 100));
        }
        if (hit.getChunkIndex() != null) {
            parts.add("块 #" + hit.getChunkIndex());
        }
        if (StringUtils.hasText(hit.getContent())) {
            String compact = hit.getContent().trim().replaceAll("\\s+", " ");
            parts.add(compact.length() > 80 ? compact.substring(0, 80) + "…" : compact);
        }
        return String.join(" · ", parts);
    }

    /**
     * 按字段形状读取报告 JSON；空白列使用对应的空集合，解析失败立即终止。
     *
     * @param raw 数据库 JSON 文本，可为空
     * @param type 字段的完整泛型类型
     * @param empty 空白列对应的空集合
     * @return 解析结果或空集合
     */
    private <T> T readJson(String raw, TypeReference<T> type, T empty) {
        if (!StringUtils.hasText(raw)) {
            return empty;
        }
        try {
            return objectMapper.readValue(raw, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("职业发展报告内容无法读取", exception);
        }
    }

    /**
     * 把报告字段写成 JSON 文本。空值保持为空列；无法序列化时保留原因并终止当前操作。
     *
     * @param val 待写入的快照、正文或质量标记，可以为空
     * @return JSON 文本；{@code val} 为空时返回 null
     */
    private String toJson(Object val) {
        if (val == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(val);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("职业发展报告内容无法序列化", exception);
        }
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /** 报告生成/润色使用的「今天」日期（Asia/Shanghai，yyyy-MM-dd）。 */
    private static String reportContextDate() {
        return LocalDate.now(DateTimeUtil.getDefaultZoneId()).format(REPORT_DATE_FMT);
    }

    /** 大模型结构化输出：报告正文 */
    @Data
    private static class LlmReportPayload {
        /** 结构化报告正文，生成或润色成功时必须为非空对象。 */
        private Map<String, Object> reportContent;
    }
}
