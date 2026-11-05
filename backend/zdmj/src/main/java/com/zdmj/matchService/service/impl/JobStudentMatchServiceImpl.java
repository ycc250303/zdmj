package com.zdmj.matchService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.StructuredModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.jobService.api.JobRole;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.dto.DimensionMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.dto.MatchWeightConfigResponse;
import com.zdmj.matchService.entity.JobStudentMatch;
import com.zdmj.matchService.enums.MatchDimension;
import com.zdmj.matchService.mapper.JobStudentMatchMapper;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 人岗匹配分析服务实现。
 *
 * <p>核心思路：把「岗位画像七维 + 关键词 + 学生画像七维」通过结构化 Prompt 喂给 LLM，让模型按
 * 「基础要求 / 职业技能 / 职业素养 / 发展潜力」四维做对比分析与打分，再用代码兜底重算「关键技能
 * 匹配率」与「综合分」，保证权重设置严格生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobStudentMatchServiceImpl
        extends ServiceImpl<JobStudentMatchMapper, JobStudentMatch>
        implements JobStudentMatchService {

    private static final String MATCH_PROMPT_DIRECTORY = "job-student-match/";

    private final JobService jobService;
    private final JobCapabilityProfileService jobCapabilityProfileService;
    private final StudentCapabilityProfileService studentCapabilityProfileService;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PageDTO<JobStudentMatchListItemResponse> getMyPage(Integer page, Integer limit) {
        Long userId = UserHolder.requireUserId();
        PageRequests.Normalized paging = PageRequests.normalize(page, limit);
        IPage<JobStudentMatchListItemResponse> result =
                baseMapper.selectMyMatchPage(PageRequests.toPage(paging), userId);
        return PageDTO.from(result);
    }

    @Override
    public JobStudentMatchResponse getOrNull(Long jobId) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "jobId不能为空");
        }
        JobStudentMatch match = getOne(new LambdaQueryWrapper<JobStudentMatch>()
                .eq(JobStudentMatch::getUserId, userId)
                .eq(JobStudentMatch::getJobId, jobId));
        if (match == null) {
            return null;
        }
        return toDto(match);
    }

    /**
     * 生成人岗匹配。读取与模型调用在事务外完成，结果在短事务中按用户与岗位覆盖保存。
     *
     * @param jobId 岗位编号
     * @param req   自定义权重，可为 null
     * @return 匹配结果
     */
    @Override
    public JobStudentMatchResponse generate(Long jobId, JobStudentMatchGenerateRequest req) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "jobId不能为空");
        }

        // 1. 岗位详情 + 岗位画像（缺失则自动生成）
        JobListItemResponse jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        JobCapabilityProfileResponse jobProfile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        if (jobProfile == null) {
            log.info("岗位画像缺失，自动生成: jobId={}", jobId);
            jobProfile = jobCapabilityProfileService.getJobCapabilityProfile(jobId);
        }

        // 2. 学生画像（必须存在）
        StudentCapabilityProfileResponse studentProfile = studentCapabilityProfileService.getCurrentUserProfileOrNull();
        if (studentProfile == null) {
            throw new BusinessException(ErrorCode.MATCH_PRECONDITION_MISSING);
        }

        // 3. 解析权重（默认 + 覆盖 → 归一化）
        JobRole role = JobRole.fromString(jobProfile.getTargetRoleType());
        MatchWeightConfigResponse weights = MatchWeightResolver.resolve(
                role, req == null ? null : req.getWeights());
        String promptName = MATCH_PROMPT_DIRECTORY + role.slug();
        log.info("人岗匹配开始: jobId={}, userId={}, role={}, prompt={}", jobId, userId, role, promptName);

        // 4. 拼装 LLM 上下文
        List<String> jobKeywords = jobDetail.getKeywords() == null
                ? List.of()
                : jobDetail.getKeywords().stream().filter(StringUtils::hasText).map(String::trim).toList();
        String userMessage = buildUserMessage(jobDetail, jobProfile, studentProfile, weights, jobKeywords);

        // 5. 调用 LLM 结构化输出（promptVars 传 null，跳过模板渲染）
        JobStudentMatchResponse aiResult;
        try {
            aiResult = modelGateway.generateStructured(CurrentActor.of(userId),
                    StructuredModelRequest.of(userMessage, promptName, null, JobStudentMatchResponse.class));
        } catch (IllegalStateException e) {
            log.error("人岗匹配结构化输出解析失败 jobId={} userId={}", jobId, userId, e);
            throw new BusinessException(ErrorCode.MATCH_GENERATION_FAILED);
        } catch (Exception e) {
            log.error("人岗匹配 LLM 调用失败 jobId={} userId={}", jobId, userId, e);
            throw new BusinessException(ErrorCode.MATCH_GENERATION_FAILED);
        }
        if (aiResult == null || aiResult.getDimensions() == null) {
            throw new BusinessException(ErrorCode.MATCH_GENERATION_FAILED);
        }

        // 6. 兜底重算关键词匹配率 + 综合分
        Map<String, DimensionMatchResponse> dims = sanitizeDimensions(aiResult.getDimensions());
        KeywordMatchResult kw = recomputeKeywordMatch(jobKeywords, aiResult.getMatchedKeywords(), studentProfile);
        int basicScore = clamp(getDimensionScore(dims, MatchDimension.BASIC));
        int skillScore = clamp(getDimensionScore(dims, MatchDimension.PROFESSIONAL_SKILL));
        int qualityScore = clamp(getDimensionScore(dims, MatchDimension.PROFESSIONAL_QUALITY));
        int potentialScore = clamp(getDimensionScore(dims, MatchDimension.DEVELOPMENT_POTENTIAL));
        int overall = MatchWeightResolver.weightedOverall(weights, basicScore, skillScore, qualityScore, potentialScore);

        JobStudentMatch entity = buildMatch(userId, jobId, jobProfile, promptName, weights,
                basicScore, skillScore, qualityScore, potentialScore, overall,
                dims, aiResult.getMatchedHighlights(), aiResult.getCriticalGaps(),
                kw.matched, kw.missing, kw.rate, aiResult.getSummary());
        if (jobService.getDetail(jobId) == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        // 7. 落库
        JobStudentMatch saved = transactionTemplate.execute(status -> saveMatch(entity));
        if (saved == null) {
            throw new IllegalStateException("人岗匹配结果未写入");
        }
        return toDto(saved);
    }

    @Override
    public MatchWeightConfigResponse getDefaultWeights(Long jobId) {
        if (jobId == null) {
            return MatchWeightResolver.defaultFor(JobRole.UNKNOWN);
        }
        JobCapabilityProfileResponse profile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        JobRole role = profile == null ? JobRole.UNKNOWN
                : JobRole.fromString(profile.getTargetRoleType());
        return MatchWeightResolver.defaultFor(role);
    }

    // ============================================================
    // Prompt 上下文拼装
    // ============================================================

    /**
     * 把岗位详情 + 岗位画像 + 学生画像 + 权重 + 关键词拼成结构化的 user message，
     * Prompt 中通过 {weightsJson} 与 {jobKeywords} 取值，正文里用 Markdown 段落组织。
     */
    private String buildUserMessage(JobListItemResponse jobDetail,
                                    JobCapabilityProfileResponse jobProfile,
                                    StudentCapabilityProfileResponse studentProfile,
                                    MatchWeightConfigResponse weights,
                                    List<String> jobKeywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是用于人岗匹配分析的全部上下文。请严格基于这些文本输出 JSON 结果。\n\n");

        sb.append("## 岗位基础信息\n");
        sb.append("- 岗位名称：").append(safe(jobDetail.getJobName())).append('\n');
        sb.append("- 公司名称：").append(safe(jobDetail.getCompanyName())).append('\n');
        sb.append("- 工作地点：").append(safe(jobDetail.getLocation())).append('\n');
        sb.append("- 岗位描述：").append(safe(jobDetail.getDescription())).append('\n');
        sb.append("- 岗位职责：").append(joinList(jobDetail.getJobDuties())).append('\n');
        sb.append("- 岗位要求：").append(joinList(jobDetail.getJobRequirements())).append('\n');
        sb.append("- 岗位关键词：").append(joinList(jobKeywords)).append("\n\n");

        sb.append("## 岗位能力画像（七维）\n");
        appendCapabilityRow(sb, "专业技能", jobProfile.getProfessionalSkills());
        appendCapabilityRow(sb, "证书", jobProfile.getCertificates());
        appendCapabilityRow(sb, "创新能力", jobProfile.getInnovationAbility());
        appendCapabilityRow(sb, "学习能力", jobProfile.getLearningAbility());
        appendCapabilityRow(sb, "抗压能力", jobProfile.getPressureResistance());
        appendCapabilityRow(sb, "沟通能力", jobProfile.getCommunicationAbility());
        appendCapabilityRow(sb, "实习/实践能力", jobProfile.getPracticalAbility());
        if (StringUtils.hasText(jobProfile.getSummary())) {
            sb.append("- 岗位总结：").append(jobProfile.getSummary()).append('\n');
        }
        if (jobProfile.getStrengths() != null && !jobProfile.getStrengths().isEmpty()) {
            sb.append("- 岗位优势点：").append(String.join("；", jobProfile.getStrengths())).append('\n');
        }
        if (jobProfile.getMissingSkills() != null && !jobProfile.getMissingSkills().isEmpty()) {
            sb.append("- 补充要求（JD 未写明的该方向常见门槛）：")
                    .append(String.join("；", jobProfile.getMissingSkills())).append('\n');
        }
        sb.append('\n');

        sb.append("## 学生就业能力画像（七维）\n");
        appendCapabilityRow(sb, "专业技能", studentProfile.getProfessionalSkills());
        appendCapabilityRow(sb, "获奖经历", studentProfile.getHonorsAndAwards());
        appendCapabilityRow(sb, "创新能力", studentProfile.getInnovationAbility());
        appendCapabilityRow(sb, "学习能力", studentProfile.getLearningAbility());
        appendCapabilityRow(sb, "抗压能力", studentProfile.getPressureResistance());
        appendCapabilityRow(sb, "沟通能力", studentProfile.getCommunicationAbility());
        appendCapabilityRow(sb, "实习/实践能力", studentProfile.getPracticalAbility());
        if (studentProfile.getStrengths() != null && !studentProfile.getStrengths().isEmpty()) {
            sb.append("- 学生优势点：").append(String.join("；", studentProfile.getStrengths())).append('\n');
        }
        if (studentProfile.getSuggestions() != null && !studentProfile.getSuggestions().isEmpty()) {
            String gapSummary = studentProfile.getSuggestions().stream()
                    .filter(s -> s != null && StringUtils.hasText(s.getIssue()))
                    .limit(6)
                    .map(s -> {
                        String cat = StringUtils.hasText(s.getCategory()) ? s.getCategory() + "：" : "";
                        return cat + s.getIssue().trim();
                    })
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("");
            if (StringUtils.hasText(gapSummary)) {
                sb.append("- 学生改进建议摘要：").append(gapSummary).append('\n');
            }
        }
        if (StringUtils.hasText(studentProfile.getSummary())) {
            sb.append("- 学生画像总结：").append(studentProfile.getSummary()).append('\n');
        }
        sb.append('\n');

        sb.append("## 评分要求\n");
        sb.append("- 各维度独立打分（0~100 整数）；\n");
        sb.append("- 关键词命中必须以「岗位关键词数组」为基准，不得新增；\n");
        sb.append("- `matchedKeywords` / `missingKeywords` 必须是岗位关键词的真子集；\n");
        sb.append("- 不要在 JSON 中输出综合分（系统会按权重重算）；\n");
        sb.append("- 权重配置（仅参考）：").append(toJsonOrEmpty(weights)).append('\n');

        return sb.toString();
    }

    private static void appendCapabilityRow(StringBuilder sb, String label, String value) {
        sb.append("- ").append(label).append("：").append(safe(value)).append('\n');
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "未提供";
        }
        return String.join("；", values);
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未提供";
    }

    /**
     * 把权重写成提示词中的 JSON。权重为空时写入空对象；无法序列化时保留原因并终止本次匹配。
     *
     * @param value 权重配置，可以为空
     * @return JSON 文本
     */
    private String toJsonOrEmpty(Object value) {
        if (value == null) {
            return "{}";
        }
        return writeJson(value);
    }

    /**
     * LLM 输出的 dimensions 可能缺维或多维，这里把它统一成 4 维。
     */
    private Map<String, DimensionMatchResponse> sanitizeDimensions(Map<String, DimensionMatchResponse> raw) {
        Map<String, DimensionMatchResponse> out = new LinkedHashMap<>();
        for (MatchDimension d : MatchDimension.values()) {
            DimensionMatchResponse got = raw == null ? null : raw.get(d.getCode());
            if (got == null) {
                got = new DimensionMatchResponse();
                got.setScore(0);
                got.setEvidence(List.of());
            }
            if (got.getScore() == null) {
                got.setScore(0);
            }
            out.put(d.getCode(), got);
        }
        return out;
    }

    private static int getDimensionScore(Map<String, DimensionMatchResponse> dims, MatchDimension d) {
        DimensionMatchResponse got = dims.get(d.getCode());
        if (got == null || got.getScore() == null) {
            return 0;
        }
        return got.getScore();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /**
     * 关键技能匹配率兜底：以 student 画像七维拼成的 corpus 为基准，对岗位关键词做包含判断；
     * 若 LLM 自报的 matchedKeywords 与 corpus 不一致，以 corpus 命中为准。
     */
    private KeywordMatchResult recomputeKeywordMatch(
            List<String> jobKeywords,
            List<String> aiMatchedKeywords,
            StudentCapabilityProfileResponse studentProfile) {

        Set<String> jobSet = new LinkedHashSet<>(jobKeywords == null ? List.of() : jobKeywords);
        if (jobSet.isEmpty()) {
            return new KeywordMatchResult(List.of(), List.of(), BigDecimal.ZERO);
        }

        String corpus = buildStudentCorpus(studentProfile).toLowerCase(Locale.ROOT);
        Set<String> aiClaim = aiMatchedKeywords == null
                ? Set.of()
                : new LinkedHashSet<>(aiMatchedKeywords);

        List<String> matched = new java.util.ArrayList<>();
        List<String> missing = new java.util.ArrayList<>();
        for (String kw : jobSet) {
            if (!StringUtils.hasText(kw)) {
                continue;
            }
            String key = kw.trim().toLowerCase(Locale.ROOT);
            boolean hitInCorpus = corpus.contains(key);
            boolean claimedByAi = aiClaim.stream()
                    .filter(StringUtils::hasText)
                    .anyMatch(s -> s.trim().equalsIgnoreCase(kw.trim()));
            // 命中条件：corpus 包含 或 LLM 主动声明（LLM 可能识别同义词，corpus 兜底）
            if (hitInCorpus || claimedByAi) {
                matched.add(kw);
            } else {
                missing.add(kw);
            }
        }
        BigDecimal rate = BigDecimal.valueOf(matched.size())
                .divide(BigDecimal.valueOf(jobSet.size()), 4, RoundingMode.HALF_UP);
        return new KeywordMatchResult(matched, missing, rate);
    }

    private static String buildStudentCorpus(StudentCapabilityProfileResponse p) {
        if (p == null) {
            return "";
        }
        return String.join(" ", Arrays.asList(
                Objects.toString(p.getProfessionalSkills(), ""),
                Objects.toString(p.getHonorsAndAwards(), ""),
                Objects.toString(p.getInnovationAbility(), ""),
                Objects.toString(p.getLearningAbility(), ""),
                Objects.toString(p.getPressureResistance(), ""),
                Objects.toString(p.getCommunicationAbility(), ""),
                Objects.toString(p.getPracticalAbility(), ""),
                Objects.toString(p.getSummary(), ""),
                p.getStrengths() == null ? "" : String.join(" ", p.getStrengths())));
    }

    private record KeywordMatchResult(List<String> matched, List<String> missing, BigDecimal rate) {
    }

    // ============================================================
    // 落库 / DTO 互转
    // ============================================================

    @SuppressWarnings("checkstyle:ParameterNumber")
    private JobStudentMatch buildMatch(Long userId, Long jobId,
                                   JobCapabilityProfileResponse jobProfile,
                                   String promptName,
                                   MatchWeightConfigResponse weights,
                                   int basicScore, int skillScore,
                                   int qualityScore, int potentialScore, int overall,
                                   Map<String, DimensionMatchResponse> dims,
                                   List<String> matchedHighlights,
                                   List<String> criticalGaps,
                                   List<String> matchedKeywords,
                                   List<String> missingKeywords,
                                   BigDecimal keySkillMatchRate,
                                   String summary) {
        JobStudentMatch entity = new JobStudentMatch();
        entity.setUserId(userId);
        entity.setJobId(jobId);
        entity.setOverallScore(overall);
        entity.setBasicScore(basicScore);
        entity.setProfessionalSkillScore(skillScore);
        entity.setProfessionalQualityScore(qualityScore);
        entity.setDevelopmentPotentialScore(potentialScore);
        entity.setWeights(toJson(weights));
        entity.setDimensionDetail(toJson(dims));
        entity.setMatchedHighlights(matchedHighlights == null ? List.of() : matchedHighlights);
        entity.setCriticalGaps(criticalGaps == null ? List.of() : criticalGaps);
        entity.setMatchedKeywords(matchedKeywords == null ? List.of() : matchedKeywords);
        entity.setMissingKeywords(missingKeywords == null ? List.of() : missingKeywords);
        entity.setKeySkillMatchRate(keySkillMatchRate == null ? BigDecimal.ZERO : keySkillMatchRate);
        entity.setSummary(summary);
        entity.setTargetRoleType(StringUtils.hasText(jobProfile.getTargetRoleType())
                ? jobProfile.getTargetRoleType() : "default");
        entity.setPromptName(promptName);
        return entity;
    }

    /**
     * 在当前事务中按用户与岗位保存匹配结果。
     * 重新读取已有记录：不存在则插入，已存在则覆盖；并发插入撞上唯一约束时改为更新该行。
     *
     * @param draft 已算好分数和说明的匹配结果，须带用户编号与岗位编号
     * @return 已写入的匹配结果
     */
    JobStudentMatch saveMatch(JobStudentMatch draft) {
        if (draft.getUserId() == null || draft.getJobId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "匹配结果缺少用户或岗位");
        }
        JobStudentMatch existing = findOwned(draft.getUserId(), draft.getJobId());
        if (existing != null) {
            return updateOwned(draft, existing);
        }
        try {
            if (baseMapper.insert(draft) != 1) {
                throw new BusinessException(ErrorCode.MATCH_GENERATION_FAILED);
            }
            return draft;
        } catch (DuplicateKeyException exception) {
            JobStudentMatch raced = findOwned(draft.getUserId(), draft.getJobId());
            if (raced == null) {
                throw exception;
            }
            return updateOwned(draft, raced);
        }
    }

    private JobStudentMatch updateOwned(JobStudentMatch draft, JobStudentMatch existing) {
        if (!draft.getUserId().equals(existing.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        draft.setId(existing.getId());
        if (baseMapper.updateById(draft) != 1) {
            throw new BusinessException(ErrorCode.MATCH_GENERATION_FAILED);
        }
        return draft;
    }

    private JobStudentMatch findOwned(Long userId, Long jobId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<JobStudentMatch>()
                .eq(JobStudentMatch::getUserId, userId)
                .eq(JobStudentMatch::getJobId, jobId));
    }

    /**
     * 把匹配字段写成 JSON 文本。空值保持为空列；无法序列化时保留原因并终止当前操作。
     *
     * @param value 待写入的权重或维度明细，可以为空
     * @return JSON 文本；{@code value} 为空时返回 null
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人岗匹配结果无法序列化", exception);
        }
    }

    private JobStudentMatchResponse toDto(JobStudentMatch entity) {
        if (entity == null) {
            return null;
        }
        JobStudentMatchResponse dto = new JobStudentMatchResponse();
        dto.setJobId(entity.getJobId());
        dto.setTargetRoleType(entity.getTargetRoleType());
        dto.setOverallScore(entity.getOverallScore());

        Map<String, DimensionMatchResponse> dims = readJson(entity.getDimensionDetail(),
                new TypeReference<Map<String, DimensionMatchResponse>>() {});
        dto.setDimensions(dims == null ? new LinkedHashMap<>() : dims);

        MatchWeightConfigResponse weights = readJson(entity.getWeights(),
                new TypeReference<MatchWeightConfigResponse>() {});
        dto.setWeights(weights);

        dto.setMatchedHighlights(entity.getMatchedHighlights());
        dto.setCriticalGaps(entity.getCriticalGaps());
        dto.setMatchedKeywords(entity.getMatchedKeywords());
        dto.setMissingKeywords(entity.getMissingKeywords());
        dto.setKeySkillMatchRate(entity.getKeySkillMatchRate());
        dto.setSummary(entity.getSummary());
        return dto;
    }

    /**
     * 读取匹配结果中的 JSON 列。空白列表示该字段为空；内容无法解析时保留原因并终止当前操作。
     *
     * @param raw 数据库中的 JSON 文本，可以为空
     * @param typeRef 目标类型
     * @return 解析结果；空白文本返回 null
     */
    private <T> T readJson(String raw, TypeReference<T> typeRef) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人岗匹配结果无法读取", exception);
        }
    }
}
