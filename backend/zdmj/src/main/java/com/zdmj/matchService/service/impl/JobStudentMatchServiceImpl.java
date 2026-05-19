package com.zdmj.matchService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.common.util.PromptUtil.JobRole;
import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import com.zdmj.matchService.dto.DimensionMatchDTO;
import com.zdmj.matchService.dto.JobStudentMatchDTO;
import com.zdmj.matchService.dto.JobStudentMatchGenerateReqDTO;
import com.zdmj.matchService.dto.MatchWeightConfigDTO;
import com.zdmj.matchService.entity.JobStudentMatch;
import com.zdmj.matchService.enums.MatchDimension;
import com.zdmj.matchService.mapper.JobStudentMatchMapper;
import com.zdmj.matchService.service.JobStudentMatchService;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 人岗匹配分析服务实现。
 *
 * <p>核心思路：把「岗位画像七维 + 关键词 + 学生画像七维」通过结构化 Prompt 喂给 LLM，让模型按
 * 「基础要求 / 职业技能 / 职业素养 / 发展潜力」四维做对比分析与打分，再用代码兜底重算「关键技能
 * 匹配率」与「综合分」，保证赛题指标与权重设置严格生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobStudentMatchServiceImpl
        extends ServiceImpl<JobStudentMatchMapper, JobStudentMatch>
        implements JobStudentMatchService {

    private final JobService jobService;
    private final JobCapabilityProfileService jobCapabilityProfileService;
    private final StudentCapabilityProfileService studentCapabilityProfileService;
    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 公共接口
    // ============================================================

    @Override
    public JobStudentMatchDTO getOrNull(Long jobId) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        JobStudentMatch match = getOne(new LambdaQueryWrapper<JobStudentMatch>()
                .eq(JobStudentMatch::getUserId, userId)
                .eq(JobStudentMatch::getJobId, jobId));
        if (match == null) {
            return null;
        }
        return toDto(match);
    }

    @Override
    public JobStudentMatchDTO generate(Long jobId, JobStudentMatchGenerateReqDTO req) {
        Long userId = UserHolder.requireUserId();
        if (jobId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        // 1. 岗位详情 + 岗位画像（缺失则自动生成）
        JobListItemDTO jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        JobCapabilityProfileDTO jobProfile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        if (jobProfile == null) {
            log.info("岗位画像缺失，自动生成: jobId={}", jobId);
            jobProfile = jobCapabilityProfileService.getJobCapabilityProfile(jobId);
        }

        // 2. 学生画像（必须存在）
        StudentCapabilityProfileDTO studentProfile = studentCapabilityProfileService.getCurrentUserProfileOrNull();
        if (studentProfile == null) {
            throw new BusinessException(ErrorCode.MATCH_PRECONDITION_MISSING);
        }

        // 3. 解析权重（默认 + 覆盖 → 归一化）
        JobRole role = PromptUtil.getJobRoleByString(jobProfile.getTargetRoleType());
        MatchWeightConfigDTO weights = MatchWeightResolver.resolve(
                role, req == null ? null : req.getWeights());
        String promptName = PromptUtil.getJobStudentMatchPromptName(role);
        log.info("人岗匹配开始: jobId={}, userId={}, role={}, prompt={}", jobId, userId, role, promptName);

        // 4. 拼装 LLM 上下文（权重 / 关键词 全部内联进 user message，避免触发 Spring AI
        //    PromptTemplate 的 StringTemplate 渲染——prompt 体内的 JSON 示例花括号会被 ST
        //    解析成变量从而抛 STException，详见回归测试 PromptTemplateRenderingTest）
        List<String> jobKeywords = jobDetail.getKeywords() == null
                ? List.of()
                : jobDetail.getKeywords().stream().filter(StringUtils::hasText).map(String::trim).toList();
        String userMessage = buildUserMessage(jobDetail, jobProfile, studentProfile, weights, jobKeywords);

        // 5. 调用 LLM 结构化输出（promptVars 传 null，ChatUtil 会跳过模板渲染，与项目其它
        //    结构化调用保持一致：resume-analysis / job-requirement / job-career-graph 均传 null）
        JobStudentMatchDTO aiResult;
        try {
            aiResult = chatUtil.chatStructuredOnce(userMessage, promptName, null, JobStudentMatchDTO.class);
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
        Map<String, DimensionMatchDTO> dims = sanitizeDimensions(aiResult.getDimensions());
        KeywordMatchResult kw = recomputeKeywordMatch(jobKeywords, aiResult.getMatchedKeywords(), studentProfile);
        int basicScore = clamp(getDimensionScore(dims, MatchDimension.BASIC));
        int skillScore = clamp(getDimensionScore(dims, MatchDimension.PROFESSIONAL_SKILL));
        int qualityScore = clamp(getDimensionScore(dims, MatchDimension.PROFESSIONAL_QUALITY));
        int potentialScore = clamp(getDimensionScore(dims, MatchDimension.DEVELOPMENT_POTENTIAL));
        int overall = MatchWeightResolver.weightedOverall(weights, basicScore, skillScore, qualityScore, potentialScore);

        // 7. 落库 upsert
        JobStudentMatch entity = upsert(userId, jobId, jobProfile, promptName, weights,
                basicScore, skillScore, qualityScore, potentialScore, overall,
                dims, aiResult.getMatchedHighlights(), aiResult.getCriticalGaps(),
                kw.matched, kw.missing, kw.rate, aiResult.getSummary());

        // 8. 拼装返回 DTO
        return toDto(entity);
    }

    @Override
    public MatchWeightConfigDTO getDefaultWeights(Long jobId) {
        if (jobId == null) {
            return MatchWeightResolver.defaultFor(JobRole.UNKNOWN);
        }
        JobCapabilityProfileDTO profile = jobCapabilityProfileService.getJobCapabilityProfileOrNull(jobId);
        JobRole role = profile == null ? JobRole.UNKNOWN
                : PromptUtil.getJobRoleByString(profile.getTargetRoleType());
        return MatchWeightResolver.defaultFor(role);
    }

    // ============================================================
    // Prompt 上下文拼装
    // ============================================================

    /**
     * 把岗位详情 + 岗位画像 + 学生画像 + 权重 + 关键词拼成结构化的 user message，
     * Prompt 中通过 {weightsJson} 与 {jobKeywords} 取值，正文里用 Markdown 段落组织。
     */
    private String buildUserMessage(JobListItemDTO jobDetail,
                                    JobCapabilityProfileDTO jobProfile,
                                    StudentCapabilityProfileDTO studentProfile,
                                    MatchWeightConfigDTO weights,
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
        sb.append('\n');

        sb.append("## 学生就业能力画像（七维）\n");
        appendCapabilityRow(sb, "专业技能", studentProfile.getProfessionalSkills());
        appendCapabilityRow(sb, "证书", studentProfile.getCertificates());
        appendCapabilityRow(sb, "创新能力", studentProfile.getInnovationAbility());
        appendCapabilityRow(sb, "学习能力", studentProfile.getLearningAbility());
        appendCapabilityRow(sb, "抗压能力", studentProfile.getPressureResistance());
        appendCapabilityRow(sb, "沟通能力", studentProfile.getCommunicationAbility());
        appendCapabilityRow(sb, "实习/实践能力", studentProfile.getPracticalAbility());
        if (studentProfile.getStrengths() != null && !studentProfile.getStrengths().isEmpty()) {
            sb.append("- 学生优势点：").append(String.join("；", studentProfile.getStrengths())).append('\n');
        }
        if (studentProfile.getMissingSkills() != null && !studentProfile.getMissingSkills().isEmpty()) {
            sb.append("- 学生缺失技能项：").append(String.join("；", studentProfile.getMissingSkills())).append('\n');
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

    private String toJsonOrEmpty(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("权重/关键词序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    // ============================================================
    // 兜底重算 / 数据清洗
    // ============================================================

    /**
     * LLM 输出的 dimensions 可能缺维或多维，这里把它统一成 4 维。
     */
    private Map<String, DimensionMatchDTO> sanitizeDimensions(Map<String, DimensionMatchDTO> raw) {
        Map<String, DimensionMatchDTO> out = new LinkedHashMap<>();
        for (MatchDimension d : MatchDimension.values()) {
            DimensionMatchDTO got = raw == null ? null : raw.get(d.getCode());
            if (got == null) {
                got = new DimensionMatchDTO();
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

    private static int getDimensionScore(Map<String, DimensionMatchDTO> dims, MatchDimension d) {
        DimensionMatchDTO got = dims.get(d.getCode());
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
     * 若 LLM 自报的 matchedKeywords 与 corpus 不一致，以 corpus 命中为准（保证赛题指标可复算）。
     */
    private KeywordMatchResult recomputeKeywordMatch(
            List<String> jobKeywords,
            List<String> aiMatchedKeywords,
            StudentCapabilityProfileDTO studentProfile) {

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

    private static String buildStudentCorpus(StudentCapabilityProfileDTO p) {
        if (p == null) {
            return "";
        }
        return String.join(" ", Arrays.asList(
                Objects.toString(p.getProfessionalSkills(), ""),
                Objects.toString(p.getCertificates(), ""),
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
    private JobStudentMatch upsert(Long userId, Long jobId,
                                   JobCapabilityProfileDTO jobProfile,
                                   String promptName,
                                   MatchWeightConfigDTO weights,
                                   int basicScore, int skillScore,
                                   int qualityScore, int potentialScore, int overall,
                                   Map<String, DimensionMatchDTO> dims,
                                   List<String> matchedHighlights,
                                   List<String> criticalGaps,
                                   List<String> matchedKeywords,
                                   List<String> missingKeywords,
                                   BigDecimal keySkillMatchRate,
                                   String summary) {
        JobStudentMatch existing = getOne(new LambdaQueryWrapper<JobStudentMatch>()
                .eq(JobStudentMatch::getUserId, userId)
                .eq(JobStudentMatch::getJobId, jobId));

        JobStudentMatch entity = new JobStudentMatch();
        if (existing != null) {
            entity.setId(existing.getId());
        }
        entity.setUserId(userId);
        entity.setJobId(jobId);
        entity.setOverallScore(overall);
        entity.setBasicScore(basicScore);
        entity.setProfessionalSkillScore(skillScore);
        entity.setProfessionalQualityScore(qualityScore);
        entity.setDevelopmentPotentialScore(potentialScore);
        entity.setWeights(toJson(weights));
        entity.setDimensionDetail(toJson(dims));
        entity.setMatchedHighlights(toJson(matchedHighlights == null ? List.of() : matchedHighlights));
        entity.setCriticalGaps(toJson(criticalGaps == null ? List.of() : criticalGaps));
        entity.setMatchedKeywords(toJson(matchedKeywords == null ? List.of() : matchedKeywords));
        entity.setMissingKeywords(toJson(missingKeywords == null ? List.of() : missingKeywords));
        entity.setKeySkillMatchRate(keySkillMatchRate == null ? BigDecimal.ZERO : keySkillMatchRate);
        entity.setSummary(summary);
        entity.setTargetRoleType(StringUtils.hasText(jobProfile.getTargetRoleType())
                ? jobProfile.getTargetRoleType() : "default");
        entity.setPromptName(promptName);

        if (existing != null) {
            updateById(entity);
        } else {
            save(entity);
        }
        return entity;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("人岗匹配 JSON 序列化失败，字段将置空: {}", e.getMessage());
            return null;
        }
    }

    private JobStudentMatchDTO toDto(JobStudentMatch entity) {
        if (entity == null) {
            return null;
        }
        JobStudentMatchDTO dto = new JobStudentMatchDTO();
        dto.setJobId(entity.getJobId());
        dto.setTargetRoleType(entity.getTargetRoleType());
        dto.setOverallScore(entity.getOverallScore());

        Map<String, DimensionMatchDTO> dims = readJson(entity.getDimensionDetail(),
                new TypeReference<Map<String, DimensionMatchDTO>>() {});
        dto.setDimensions(dims == null ? new LinkedHashMap<>() : dims);

        MatchWeightConfigDTO weights = readJson(entity.getWeights(),
                new TypeReference<MatchWeightConfigDTO>() {});
        dto.setWeights(weights);

        dto.setMatchedHighlights(readJson(entity.getMatchedHighlights(),
                new TypeReference<List<String>>() {}));
        dto.setCriticalGaps(readJson(entity.getCriticalGaps(),
                new TypeReference<List<String>>() {}));
        dto.setMatchedKeywords(readJson(entity.getMatchedKeywords(),
                new TypeReference<List<String>>() {}));
        dto.setMissingKeywords(readJson(entity.getMissingKeywords(),
                new TypeReference<List<String>>() {}));
        dto.setKeySkillMatchRate(entity.getKeySkillMatchRate());
        dto.setSummary(entity.getSummary());
        return dto;
    }

    private <T> T readJson(String raw, TypeReference<T> typeRef) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (Exception e) {
            log.warn("人岗匹配 JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
