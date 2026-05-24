package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.storage.FileUploadUtil;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.common.ai.PromptUtil.JobRole;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateReqDTO;
import com.zdmj.resumeService.dto.ResumeRoleDetectDTO;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCapabilityProfileServiceImpl
        extends ServiceImpl<StudentCapabilityProfileMapper, StudentCapabilityProfile>
        implements StudentCapabilityProfileService {

    /** 关键词命中达到该分数则直接采用规则结果，不调 LLM */
    private static final int KEYWORD_DIRECT_HIT_THRESHOLD = 4;

    /** scoreDetail 五项上限，与 resume-analysis 评分标准（40-20-15-15-10）一致 */
    private static final int MAX_PROJECT_EXPERIENCE_SCORE = 40;
    private static final int MAX_SKILL_MATCH_SCORE = 20;
    private static final int MAX_CONTENT_COMPLETENESS_SCORE = 15;
    private static final int MAX_STRUCTURE_CLARITY_SCORE = 15;
    private static final int MAX_EXPRESSION_PROFESSIONALISM_SCORE = 10;

    private static final int MAX_COMPETITIVENESS_SCORE = MAX_PROJECT_EXPERIENCE_SCORE + MAX_SKILL_MATCH_SCORE
            + MAX_CONTENT_COMPLETENESS_SCORE + MAX_STRUCTURE_CLARITY_SCORE + MAX_EXPRESSION_PROFESSIONALISM_SCORE;

    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;
    private final FileUploadUtil fileUploadUtil;

    private static final Map<JobRole, List<String>> KEYWORDS = Map.of(
        JobRole.JAVA, List.of("java", "spring", "spring boot", "mybatis", "mysql", "redis", "jvm"),
        JobRole.FRONTEND, List.of("react", "vue", "typescript", "javascript", "webpack", "vite", "css", "html"),
        JobRole.CPP, List.of("c++", "cpp", "stl", "cmake", "gdb", "linux", "多线程", "内存管理"),
        JobRole.SOFTWARE_TEST, List.of("测试", "test case", "pytest", "selenium", "jmeter", "postman", "缺陷"),
        JobRole.AI_AGENT, List.of("llm", "大模型", "agent", "rag", "langchain", "prompt", "embedding"),
        JobRole.ALGORITHM, List.of("算法", "machine learning", "深度学习", "pytorch", "tensorflow"),
        JobRole.DATA_ANALYST, List.of("数据分析", "sql", "tableau", "powerbi", "excel", "指标"),
        JobRole.BIG_DATA, List.of("hadoop", "spark", "flink", "hive", "数仓", "kafka"),
        JobRole.DEVOPS_SRE, List.of("devops", "sre", "k8s", "kubernetes", "docker", "ci/cd", "ansible"),
        JobRole.CYBERSECURITY, List.of("安全", "渗透", "漏洞", "owasp", "攻防", "合规"));

    @Override
    public StudentCapabilityProfileDTO getCurrentUserProfile() {
        StudentCapabilityProfileDTO dto = getCurrentUserProfileOrNull();
        if (dto == null) {
            throw new BusinessException(404, "当前用户尚未生成能力画像");
        }
        return dto;
    }

    @Override
    public StudentCapabilityProfileDTO getCurrentUserProfileOrNull() {
        Long userId = UserHolder.requireUserId();
        StudentCapabilityProfile profile = getOne(
                new LambdaQueryWrapper<StudentCapabilityProfile>()
                        .eq(StudentCapabilityProfile::getUserId, userId));
        if (profile == null) {
            return null;
        }
        StudentCapabilityProfileDTO dto = toDto(profile);
        hydrateDtoFromEntity(profile, dto);
        return dto;
    }

    /**
     * 生成能力画像
     * @param reqDTO 简历生成请求DTO
     * @return 能力画像DTO
     */
    @Override
    public StudentCapabilityProfileDTO generateProfile(CapabilityProfileGenerateReqDTO reqDTO) {
        Long userId = UserHolder.requireUserId();
        String pdfUrl = StringUtils.hasText(reqDTO.getPdfUrl()) ? reqDTO.getPdfUrl().trim() : null;
        String sourceText = resolveSourceText(reqDTO);

        ResumeRoleDetectDTO resumeRole = detect(sourceText);
        JobRole jobRole = resumeRole.getRole();
        log.info("岗位识别: role={}, confidence={}", jobRole, resumeRole.getConfidence());

        log.info("开始调用大模型生成能力画像...");
        StudentCapabilityProfileDTO aiResult;
        try {
            String promptName = PromptUtil.getResumeAnalysisPromptName(jobRole);
            log.info("使用提示词: {}", promptName);
            aiResult = chatUtil.chatStructuredOnce(sourceText, promptName, null,
                    StudentCapabilityProfileDTO.class);
            normalizeProfileScores(aiResult);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalStateException e) {
            log.error("能力画像结构化输出失败", e);
            throw new BusinessException(500, "能力画像生成失败，请稍后重试");
        } catch (Exception e) {
            log.error("大模型生成能力画像失败", e);
            throw new BusinessException(500, "大模型生成能力画像失败，请稍后重试");
        }

        // 3. 落库保存或更新
        StudentCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<StudentCapabilityProfile>()
                        .eq(StudentCapabilityProfile::getUserId, userId));

        StudentCapabilityProfile newProfile = toEntity(aiResult);
        newProfile.setUserId(userId);
        newProfile.setRoleConfidence(BigDecimal.valueOf(resumeRole.getConfidence()));
        String promptName = PromptUtil.getResumeAnalysisPromptName(jobRole);
        newProfile.setPromptName(promptName);
        newProfile.setTargetRoleType(PromptUtil.getPromptDisplayType(promptName));
        newProfile.setScoreDetail(toJson(aiResult.getScoreDetail()));
        newProfile.setSuggestions(toJson(aiResult.getSuggestions()));

        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }

        StudentCapabilityProfileDTO responseDto = toDto(newProfile);
        hydrateDtoFromEntity(newProfile, responseDto);
        mergeAiTransientFields(aiResult, responseDto);
        cleanupUploadedResumeAfterAnalysis(pdfUrl);
        return responseDto;
    }

    /**
     * 能力画像分析完成后删除 COS 临时简历；清理失败不影响主流程。
     */
    private void cleanupUploadedResumeAfterAnalysis(String pdfUrl) {
        if (!StringUtils.hasText(pdfUrl)) {
            return;
        }
        try {
            fileUploadUtil.deleteProfileUploadByUrl(pdfUrl);
        } catch (Exception e) {
            log.warn("能力画像生成成功但清理 COS 简历失败: url={}, err={}", pdfUrl, e.getMessage());
        }
    }

    private static StudentCapabilityProfileDTO toDto(StudentCapabilityProfile entity) {
        if (entity == null) {
            return null;
        }
        StudentCapabilityProfileDTO dto = new StudentCapabilityProfileDTO();
        dto.setTargetRoleType(StringUtils.hasText(entity.getTargetRoleType()) ? entity.getTargetRoleType()
                : PromptUtil.getPromptDisplayType(entity.getPromptName()));
        dto.setProfessionalSkills(entity.getProfessionalSkills());
        dto.setHonorsAndAwards(entity.getHonorsAndAwards());
        dto.setInnovationAbility(entity.getInnovationAbility());
        dto.setLearningAbility(entity.getLearningAbility());
        dto.setPressureResistance(entity.getPressureResistance());
        dto.setCommunicationAbility(entity.getCommunicationAbility());
        dto.setPracticalAbility(entity.getPracticalAbility());
        dto.setCompetitivenessScore(entity.getCompetitivenessScore());
        return dto;
    }

    private static StudentCapabilityProfile toEntity(StudentCapabilityProfileDTO dto) {
        if (dto == null) {
            return null;
        }
        StudentCapabilityProfile entity = new StudentCapabilityProfile();
        entity.setProfessionalSkills(dto.getProfessionalSkills());
        entity.setHonorsAndAwards(dto.getHonorsAndAwards());
        entity.setInnovationAbility(dto.getInnovationAbility());
        entity.setLearningAbility(dto.getLearningAbility());
        entity.setPressureResistance(dto.getPressureResistance());
        entity.setCommunicationAbility(dto.getCommunicationAbility());
        entity.setPracticalAbility(dto.getPracticalAbility());
        entity.setCompetitivenessScore(dto.getCompetitivenessScore());
        return entity;
    }

    /**
     * 实体 JSON 列存为 String，扁平字段由 toDto 拷贝；嵌套列表/对象在此反序列化补全。
     */
    private void hydrateDtoFromEntity(StudentCapabilityProfile entity, StudentCapabilityProfileDTO dto) {
        if (entity == null || dto == null) {
            return;
        }
        try {
            if (StringUtils.hasText(entity.getScoreDetail())) {
                dto.setScoreDetail(objectMapper.readValue(entity.getScoreDetail(), StudentCapabilityProfileDTO.ScoreDetail.class));
            }
            if (StringUtils.hasText(entity.getSuggestions())) {
                dto.setSuggestions(objectMapper.readValue(entity.getSuggestions(), new TypeReference<List<StudentCapabilityProfileDTO.Suggestion>>() {}));
            }
        } catch (Exception e) {
            log.warn("反序列化画像 JSON 失败: {}", e.getMessage());
        }
    }

    /**
     * strengths/summary 无独立库列时，用本次 LLM 输出补全返回 DTO（仅生成接口保证完整）。
     */
    private static void mergeAiTransientFields(StudentCapabilityProfileDTO aiResult, StudentCapabilityProfileDTO out) {
        if (aiResult == null || out == null) {
            return;
        }
        out.setStrengths(aiResult.getStrengths());
        out.setSummary(aiResult.getSummary());
    }

    /**
     * 解析简历文本
     * @param reqDTO 简历生成请求DTO
     * @return 简历文本
     */
    private String resolveSourceText(CapabilityProfileGenerateReqDTO reqDTO) {
        String sourceText;
        if (StringUtils.hasText(reqDTO.getPdfUrl())) {
            log.info("从 PDF 解析内容: {}", reqDTO.getPdfUrl());
            try {
                sourceText = PdfParserUtil.extractTextFromUrl(reqDTO.getPdfUrl());
            } catch (Exception e) {
                log.error("PDF 解析失败", e);
                throw new BusinessException(400, "PDF 解析失败，请检查文件是否合法");
            }
        } else if (StringUtils.hasText(reqDTO.getRawText())) {
            log.info("从纯文本解析内容");
            sourceText = reqDTO.getRawText();
        } else {
            throw new BusinessException(400, "必须提供 pdfUrl 或 rawText");
        }
        if (!StringUtils.hasText(sourceText)) {
            throw new BusinessException(400, "提取到的文本为空，无法生成画像");
        }
        return sourceText;
    }

    /**
     * 校验 scoreDetail 分项区间并计算 competitivenessScore。
     */
    private void normalizeProfileScores(StudentCapabilityProfileDTO dto) {
        if (dto == null) {
            return;
        }
        validateScoreDetail(dto);
        computeCompetitivenessScore(dto);
        if (dto.getCompetitivenessScore() != null) {
            int normalized = clampScore(dto.getCompetitivenessScore(), 0, MAX_COMPETITIVENESS_SCORE, "competitivenessScore");
            if (!Integer.valueOf(normalized).equals(dto.getCompetitivenessScore())) {
                log.warn("能力画像 competitivenessScore 超出 0~{}，已修正: {} -> {}",
                        MAX_COMPETITIVENESS_SCORE, dto.getCompetitivenessScore(), normalized);
            }
            dto.setCompetitivenessScore(normalized);
        }
    }

    /**
     * 校验 scoreDetail 各分项是否落在 40-20-15-15-10 合法区间；越界则抛出业务异常。
     */
    private void validateScoreDetail(StudentCapabilityProfileDTO dto) {
        StudentCapabilityProfileDTO.ScoreDetail detail = dto.getScoreDetail();
        if (detail == null) {
            return;
        }
        assertScoreInRange(detail.getProjectExperienceScore(), MAX_PROJECT_EXPERIENCE_SCORE, "projectExperienceScore");
        assertScoreInRange(detail.getSkillMatchScore(), MAX_SKILL_MATCH_SCORE, "skillMatchScore");
        assertScoreInRange(detail.getContentCompletenessScore(), MAX_CONTENT_COMPLETENESS_SCORE, "contentCompletenessScore");
        assertScoreInRange(detail.getStructureClarityScore(), MAX_STRUCTURE_CLARITY_SCORE, "structureClarityScore");
        assertScoreInRange(detail.getExpressionProfessionalismScore(), MAX_EXPRESSION_PROFESSIONALISM_SCORE,
                "expressionProfessionalismScore");
    }

    private void assertScoreInRange(Integer score, int max, String fieldName) {
        if (score == null) {
            return;
        }
        if (score < 0 || score > max) {
            throw new BusinessException(
                    ErrorCode.CAPABILITY_PROFILE_SCORE_INVALID.getCode(),
                    String.format("scoreDetail.%s 超出合法范围 0~%d，实际值 %d", fieldName, max, score));
        }
    }

    /**
     * 根据 scoreDetail 五项之和计算 competitivenessScore，忽略模型直接返回的总分。
     */
    private void computeCompetitivenessScore(StudentCapabilityProfileDTO dto) {
        if (dto == null) {
            return;
        }
        StudentCapabilityProfileDTO.ScoreDetail detail = dto.getScoreDetail();
        if (detail != null && hasAnyScoreDetailValue(detail)) {
            int sum = safeScore(detail.getProjectExperienceScore())
                    + safeScore(detail.getSkillMatchScore())
                    + safeScore(detail.getContentCompletenessScore())
                    + safeScore(detail.getStructureClarityScore())
                    + safeScore(detail.getExpressionProfessionalismScore());
            if (dto.getCompetitivenessScore() != null && dto.getCompetitivenessScore() != sum) {
                log.debug("忽略模型返回的 competitivenessScore={}，已按 scoreDetail 重算为 {}",
                        dto.getCompetitivenessScore(), sum);
            }
            dto.setCompetitivenessScore(sum);
            return;
        }
        if (dto.getCompetitivenessScore() != null && dto.getCompetitivenessScore() != 0) {
            log.debug("scoreDetail 为空，忽略模型返回的 competitivenessScore={}", dto.getCompetitivenessScore());
        }
        dto.setCompetitivenessScore(0);
    }

    private static boolean hasAnyScoreDetailValue(StudentCapabilityProfileDTO.ScoreDetail detail) {
        return detail.getProjectExperienceScore() != null
                || detail.getSkillMatchScore() != null
                || detail.getContentCompletenessScore() != null
                || detail.getStructureClarityScore() != null
                || detail.getExpressionProfessionalismScore() != null;
    }

    private static int safeScore(Integer value) {
        return value == null ? 0 : value;
    }

    private static int clampScore(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            return Math.min(max, Math.max(min, value));
        }
        return value;
    }

    /**
     * 根据简历文本判断岗位画像
     * @param resumeText 简历文本
     * @return 岗位画像
     */
    private ResumeRoleDetectDTO detect(String resumeText) {
        if (!StringUtils.hasText(resumeText)) {
            return new ResumeRoleDetectDTO(JobRole.UNKNOWN, 0.0, "简历文本为空");
        }

        // 1. 关键词规则命中
        String lower = resumeText.toLowerCase();
        JobRole bestRole = JobRole.UNKNOWN;
        int bestScore = 0;

        for (var entry : KEYWORDS.entrySet()) {
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
            double conf = Math.min(0.9, 0.45 + bestScore * 0.1);
            return new ResumeRoleDetectDTO(bestRole, conf, "关键词规则命中: " + bestScore);
        }

        try {
            RoleDetectLLMResult llmResult = chatUtil.chatStructuredOnce(resumeText, PromptNames.JOB_DETECT,
                    null, RoleDetectLLMResult.class);
            JobRole role = PromptUtil.getJobRoleByString(llmResult.getRoleCode());
            if (role == JobRole.UNKNOWN && bestRole != JobRole.UNKNOWN) {
                return new ResumeRoleDetectDTO(bestRole, 0.45,
                        "LLM 返回 unknown，采用关键词弱命中: " + bestScore);
            }
            return new ResumeRoleDetectDTO(role, llmResult.getConfidence(),
                    llmResult.getReason() != null ? llmResult.getReason() : "LLM 分类");
        } catch (Exception e) {
            log.warn("岗位分类 LLM 失败，回退关键词: {}", e.getMessage());
            if (bestRole != JobRole.UNKNOWN) {
                return new ResumeRoleDetectDTO(bestRole, 0.35, "LLM 失败，回退关键词弱命中");
            }
            return new ResumeRoleDetectDTO(JobRole.UNKNOWN, 0.2, "规则与 LLM 均未明确岗位");
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败，字段将置空: {}", e.getMessage());
            return null;
        }
    }

    @lombok.Data
    private static class RoleDetectLLMResult {
        private String roleCode;
        private double confidence;
        private String reason;
    }
}
