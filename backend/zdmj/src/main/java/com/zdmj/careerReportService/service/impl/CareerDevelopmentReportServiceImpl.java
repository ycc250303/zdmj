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
import com.zdmj.knowledgeService.api.KnowledgeRagService;
import com.zdmj.knowledgeService.api.KnowledgeHit;
import com.zdmj.knowledgeService.api.KnowledgeReference;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>负责聚合学生画像、岗位详情、人岗匹配与知识库向量检索，
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

    private static final DateTimeFormatter REPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<KnowledgeReference>> SOURCES_TYPE = new TypeReference<>() {};


    private final ObjectMapper objectMapper;
    private final ModelGateway modelGateway;
    private final JobService jobService;
    private final JobStudentMatchService jobStudentMatchService;
    private final StudentCapabilityProfileService studentCapabilityProfileService;
    private final KnowledgeRagService knowledgeRagService;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public CareerReportResponse getLatestOrNull(CurrentActor actor, Long jobId) {
        Long userId = actor.userId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "jobId不能为空");
        }
        CareerDevelopmentReport entity = getOne(new LambdaQueryWrapper<CareerDevelopmentReport>()
                .eq(CareerDevelopmentReport::getUserId, userId)
                .eq(CareerDevelopmentReport::getJobId, jobId)
                .last("LIMIT 1"));
        return entity == null ? null : toResponse(actor, entity);
    }

    @Override
    public CareerReportResponse generate(CurrentActor actor, Long jobId, CareerReportGenerateRequest req) {
        Long userId = actor.userId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "jobId不能为空");
        }
        // 1. 校验岗位与学生画像前置条件
        JobListItemResponse jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        StudentCapabilityProfileResponse studentProfile = studentCapabilityProfileService.getCurrentUserProfileOrNull(actor);
        if (studentProfile == null) {
            throw new BusinessException(ErrorCode.MATCH_PRECONDITION_MISSING);
        }
        // 2. 加载或生成人岗匹配
        JobStudentMatchResponse match = loadOrGenerateMatch(actor, jobId);
        // 3. 知识库 RAG 检索学习路径片段
        List<KnowledgeHit> ragHits;
        try {
            ragHits = knowledgeRagService.retrieveLearningPaths(actor, buildKnowledgeQuery(jobDetail, req));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.KNOWLEDGE_RETRIEVAL_FAILED) { throw exception; }
            throw new BusinessException(ErrorCode.CAREER_REPORT_GENERATION_FAILED, exception);
        }
        // 4. 调用大模型生成结构化报告
        Map<String, Object> reportContent = generateStructuredReport(
                actor, jobDetail, studentProfile, match, ragHits, req);
        // 5. 事务外组装快照并校验正文，再覆盖写入唯一一行
        CareerDevelopmentReport draft = new CareerDevelopmentReport();
        draft.setUserId(userId);
        draft.setJobId(jobId);
        draft.setMatchId(match.id());
        draft.setStudentProfileSnapshot(toJson(studentProfile));
        draft.setMatchSnapshot(toJson(match));
        draft.setKnowledgeSources(toJson(ragHits.stream().map(KnowledgeHit::reference).toList()));
        return toResponse(actor, saveReport(draft, reportContent, GENERATE_PROMPT));
    }

    @Override
    public CareerReportResponse polish(CurrentActor actor, Long reportId, CareerReportPolishRequest req) {
        CareerDevelopmentReport current = requireOwnedReport(actor, reportId);
        Map<String, Object> currentContent = readJson(current.getReportContent(), MAP_TYPE, Map.of());

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("instruction", req == null ? "" : safe(req.instruction()));
        String currentDate = reportContextDate();
        vars.put("currentDate", currentDate);

        String userMessage = "请对以下职业发展报告进行润色，保持结构不变，增强可执行性与可读性：\n"
                + "## 当前日期（北京时间）\n" + currentDate + "\n\n"
                + "若调整 evaluationPlan 中 quantitativeMetrics 的 deadline，具体日期（YYYY-MM-DD）不得早于当前日期。\n\n"
                + toJson(currentContent);
        Map<String, Object> polished = requestReportContent(actor, userMessage,
                POLISH_PROMPT, vars, ErrorCode.CAREER_REPORT_POLISH_FAILED);
        return toResponse(actor, saveReport(draftFromCurrent(current), polished, POLISH_PROMPT));
    }

    @Override
    public CareerReportCheckResponse checkIntegrity(CurrentActor actor, Long reportId) {
        CareerDevelopmentReport report = requireOwnedReport(actor, reportId);
        Map<String, Object> reportContent = readJson(report.getReportContent(), MAP_TYPE, Map.of());
        CareerReportCheckResponse local = ReportIntegrity.check(reportContent);

        CareerReportCheckResponse modelCheck = modelGateway.generateStructured(actor,
                new ModelRequest("请检查这份职业发展报告是否完整且可执行：\n" + toJson(reportContent),
                        INTEGRITY_CHECK_PROMPT, null), CareerReportCheckResponse.class);
        CareerReportCheckResponse check = ReportIntegrity.merge(local, modelCheck);
        Integer completenessScore = check.completenessScore();
        String qualityFlags = toJson(ReportIntegrity.qualityFlags(check));
        Integer status = Boolean.TRUE.equals(check.passed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED;
        transactionTemplate.executeWithoutResult(tx -> writeIntegrityResult(
                report.getId(), report.getUserId(), report.getJobId(),
                completenessScore, qualityFlags, status));
        return check;
    }

    @Override
    public CareerReportResponse saveManualEdit(CurrentActor actor, Long reportId, CareerReportUpdateRequest req) {
        CareerDevelopmentReport current = requireOwnedReport(actor, reportId);
        if (req == null || req.reportContent() == null || req.reportContent().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "报告内容不能为空");
        }
        return toResponse(actor, saveReport(draftFromCurrent(current), req.reportContent(), current.getPromptName()));
    }

    private JobStudentMatchResponse loadOrGenerateMatch(CurrentActor actor, Long jobId) {
        JobStudentMatchResponse match = jobStudentMatchService.getOrNull(actor, jobId);
        return match != null ? match : jobStudentMatchService.generate(actor, jobId, null);
    }

    private Map<String, Object> generateStructuredReport(CurrentActor actor,
                                                         JobListItemResponse jobDetail,
                                                         StudentCapabilityProfileResponse studentProfile,
                                                         JobStudentMatchResponse match,
                                                         List<KnowledgeHit> ragHits,
                                                         CareerReportGenerateRequest req) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("userPreference", req == null ? "" : safe(req.userPreference()));
        vars.put("focus", req == null ? "" : safe(req.focus()));
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
        sb.append("## 人岗匹配\n").append(toJson(match)).append("\n\n");
        sb.append("## 学习路径知识上下文\n").append(ragContext).append("\n");

        return requestReportContent(actor, sb.toString(), GENERATE_PROMPT, vars,
                ErrorCode.CAREER_REPORT_GENERATION_FAILED);
    }

    /**
     * 调用报告生成或润色模型，并校验返回正文。调用失败和空正文均终止保存。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param message 已组装的业务输入
     * @param prompt 提示词名称
     * @param variables 提示词变量
     * @param failure 模型调用失败对应的业务错误码
     * @return 非空报告正文
     * @throws BusinessException 模型调用失败或正文缺失
     */
    private Map<String, Object> requestReportContent(CurrentActor actor, String message, String prompt,
            Map<String, Object> variables, ErrorCode failure) {
        LlmReportPayload payload;
        try {
            payload = modelGateway.generateStructured(actor,
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
     *
     * @param actor 已认证操作者，包含用户编号
     */
    private CareerDevelopmentReport requireOwnedReport(CurrentActor actor, Long reportId) {
        Long userId = actor.userId();
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
        draft.setStatus(Boolean.TRUE.equals(check.passed()) ? STATUS_CHECKED : STATUS_CHECK_FAILED);
        draft.setCompletenessScore(check.completenessScore());
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

    private CareerReportResponse toResponse(CurrentActor actor, CareerDevelopmentReport entity) {
        return new CareerReportResponse(entity.getId(), entity.getJobId(),
                entity.getStatus(), entity.getCompletenessScore(), entity.getPromptName(),
                readJson(entity.getReportContent(), MAP_TYPE, Map.of()),
                readJson(entity.getQualityFlags(), MAP_TYPE, Map.of()),
                knowledgeRagService.resolveReportReferences(actor, readJson(entity.getKnowledgeSources(), SOURCES_TYPE, List.of())));
    }

    private String buildKnowledgeQuery(JobListItemResponse jobDetail, CareerReportGenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("生成职业发展学习路径 ");
        sb.append(safe(jobDetail.jobName())).append(" ");
        sb.append(safe(jobDetail.description())).append(" ");
        if (req != null) {
            sb.append(safe(req.focus())).append(" ");
            sb.append(safe(req.userPreference()));
        }
        return sb.toString();
    }

    private String buildRagContext(List<KnowledgeHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "未命中学习路径文档，请仅基于画像和匹配结果生成。";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (KnowledgeHit hit : hits) {
            sb.append("### 片段 ").append(idx++).append('\n');
            sb.append("- documentId: ").append(hit.documentId()).append('\n');
            sb.append("- score: ").append(hit.score()).append('\n');
            sb.append("- title: ").append(hit.title()).append('\n');
            sb.append("- docCategory: ").append(hit.docCategory()).append('\n');
            sb.append("- content: ").append(safe(hit.content())).append('\n');
        }
        return sb.toString();
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
