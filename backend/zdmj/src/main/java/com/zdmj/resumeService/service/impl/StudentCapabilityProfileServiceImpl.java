package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.common.ai.JobRole;
import com.zdmj.common.ai.JobRoleDetector;
import com.zdmj.common.ai.PromptScenario;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.ResumeRoleDetectDTO;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCapabilityProfileServiceImpl
        extends ServiceImpl<StudentCapabilityProfileMapper, StudentCapabilityProfile>
        implements StudentCapabilityProfileService {

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
    private final FileUploadService fileUploadService;
    private final PdfParserUtil pdfParserUtil;
    private final PromptUtil promptUtil;

    @Override
    public StudentCapabilityProfileResponse getCurrentUserProfile() {
        StudentCapabilityProfileResponse dto = getCurrentUserProfileOrNull();
        if (dto == null) {
            throw new BusinessException(ErrorCode.CAPABILITY_PROFILE_NOT_FOUND);
        }
        return dto;
    }

    @Override
    public StudentCapabilityProfileResponse getCurrentUserProfileOrNull() {
        Long userId = UserHolder.requireUserId();
        StudentCapabilityProfile profile = getOne(
                new LambdaQueryWrapper<StudentCapabilityProfile>()
                        .eq(StudentCapabilityProfile::getUserId, userId));
        if (profile == null) {
            return null;
        }
        StudentCapabilityProfileResponse dto = toDto(profile);
        hydrateDtoFromEntity(profile, dto);
        return dto;
    }

    /**
     * 生成能力画像
     * @param reqDTO 简历生成请求DTO
     * @return 能力画像DTO
     */
    @Override
    public StudentCapabilityProfileResponse generateProfile(CapabilityProfileGenerateRequest reqDTO) {
        Long userId = UserHolder.requireUserId();
        String pdfUrl = StringUtils.hasText(reqDTO.getPdfUrl()) ? reqDTO.getPdfUrl().trim() : null;
        String sourceText = resolveSourceText(reqDTO);

        ResumeRoleDetectDTO resumeRole = detect(userId, sourceText);
        JobRole jobRole = resumeRole.getRole();
        log.info("岗位识别: role={}, confidence={}", jobRole, resumeRole.getConfidence());

        log.info("开始调用大模型生成能力画像...");
        StudentCapabilityProfileResponse aiResult;
        try {
            String promptName = promptUtil.resolve(PromptScenario.RESUME_ANALYSIS, jobRole);
            log.info("使用提示词: {}", promptName);
            aiResult = chatUtil.chatStructuredOnce(userId, sourceText, promptName, null,
                    StudentCapabilityProfileResponse.class);
            normalizeProfileScores(aiResult);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalStateException e) {
            log.error("能力画像结构化输出失败", e);
            throw new BusinessException(ErrorCode.CAPABILITY_PROFILE_GENERATION_FAILED);
        } catch (Exception e) {
            log.error("大模型生成能力画像失败", e);
            throw new BusinessException(
                    ErrorCode.CAPABILITY_PROFILE_GENERATION_FAILED.getCode(),
                    "大模型生成能力画像失败，请稍后重试");
        }

        // 3. 落库保存或更新
        StudentCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<StudentCapabilityProfile>()
                        .eq(StudentCapabilityProfile::getUserId, userId));

        StudentCapabilityProfile newProfile = toEntity(aiResult);
        newProfile.setUserId(userId);
        newProfile.setRoleConfidence(BigDecimal.valueOf(resumeRole.getConfidence()));
        String promptName = promptUtil.resolve(PromptScenario.RESUME_ANALYSIS, jobRole);
        newProfile.setPromptName(promptName);
        newProfile.setTargetRoleType(jobRole.slug());
        newProfile.setScoreDetail(toJson(aiResult.getScoreDetail()));
        newProfile.setSuggestions(toJson(aiResult.getSuggestions()));

        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }

        StudentCapabilityProfileResponse responseDto = toDto(newProfile);
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
            fileUploadService.deleteOwnedByUrl(pdfUrl, "profile");
        } catch (Exception e) {
            log.warn("能力画像生成成功但清理 COS 简历失败: url={}, err={}", pdfUrl, e.getMessage());
        }
    }

    private static StudentCapabilityProfileResponse toDto(StudentCapabilityProfile entity) {
        if (entity == null) {
            return null;
        }
        StudentCapabilityProfileResponse dto = new StudentCapabilityProfileResponse();
        dto.setTargetRoleType(StringUtils.hasText(entity.getTargetRoleType()) ? entity.getTargetRoleType()
                : JobRole.fromPromptName(entity.getPromptName()).slug());
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

    private static StudentCapabilityProfile toEntity(StudentCapabilityProfileResponse dto) {
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
    private void hydrateDtoFromEntity(StudentCapabilityProfile entity, StudentCapabilityProfileResponse dto) {
        if (entity == null || dto == null) {
            return;
        }
        try {
            if (StringUtils.hasText(entity.getScoreDetail())) {
                dto.setScoreDetail(objectMapper.readValue(entity.getScoreDetail(), StudentCapabilityProfileResponse.ScoreDetail.class));
            }
            if (StringUtils.hasText(entity.getSuggestions())) {
                dto.setSuggestions(objectMapper.readValue(entity.getSuggestions(), new TypeReference<List<StudentCapabilityProfileResponse.Suggestion>>() {}));
            }
        } catch (Exception e) {
            log.warn("反序列化画像 JSON 失败: {}", e.getMessage());
        }
    }

    /**
     * strengths/summary 无独立库列时，用本次 LLM 输出补全返回 DTO（仅生成接口保证完整）。
     */
    private static void mergeAiTransientFields(StudentCapabilityProfileResponse aiResult, StudentCapabilityProfileResponse out) {
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
    private String resolveSourceText(CapabilityProfileGenerateRequest reqDTO) {
        String sourceText;
        if (StringUtils.hasText(reqDTO.getPdfUrl())) {
            log.info("从 PDF 解析内容: {}", reqDTO.getPdfUrl());
            try {
                sourceText = pdfParserUtil.extractTextFromUrl(reqDTO.getPdfUrl());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("PDF 解析失败", e);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "PDF 解析失败，请检查文件是否合法");
            }
        } else if (StringUtils.hasText(reqDTO.getRawText())) {
            log.info("从纯文本解析内容");
            sourceText = reqDTO.getRawText();
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "必须提供 pdfUrl 或 rawText");
        }
        if (!StringUtils.hasText(sourceText)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "提取到的文本为空，无法生成画像");
        }
        return sourceText;
    }

    /**
     * 校验 scoreDetail 分项区间并计算 competitivenessScore。
     */
    private void normalizeProfileScores(StudentCapabilityProfileResponse dto) {
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
    private void validateScoreDetail(StudentCapabilityProfileResponse dto) {
        StudentCapabilityProfileResponse.ScoreDetail detail = dto.getScoreDetail();
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
    private void computeCompetitivenessScore(StudentCapabilityProfileResponse dto) {
        if (dto == null) {
            return;
        }
        StudentCapabilityProfileResponse.ScoreDetail detail = dto.getScoreDetail();
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

    private static boolean hasAnyScoreDetailValue(StudentCapabilityProfileResponse.ScoreDetail detail) {
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
    private ResumeRoleDetectDTO detect(Long userId, String resumeText) {
        JobRoleDetector.DetectResult detected = JobRoleDetector.detect(userId, resumeText, chatUtil, log);
        String reason = !StringUtils.hasText(resumeText)
                ? "简历文本为空"
                : detected.reason();
        return new ResumeRoleDetectDTO(detected.role(), detected.confidence(), reason);
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
}
