package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.common.util.PromptUtil.JobRole;
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

    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;

    private static final Map<JobRole, List<String>> KEYWORDS = Map.of(
        JobRole.JAVA, List.of("java", "spring", "spring boot", "mybatis", "mysql", "redis", "jvm"),
        JobRole.FRONTEND, List.of("react", "vue", "typescript", "javascript", "webpack", "vite", "css", "html"),
        JobRole.CPP, List.of("c++", "cpp", "stl", "cmake", "gdb", "linux", "多线程", "内存管理"),
        JobRole.SOFTWARE_TEST, List.of("测试", "test case", "pytest", "selenium", "jmeter", "postman", "缺陷"));

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

    @Override
    public StudentCapabilityProfileDTO generateProfile(CapabilityProfileGenerateReqDTO reqDTO) {
        Long userId = UserHolder.requireUserId();
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
        newProfile.setTargetRoleCode(PromptUtil.getRoleCodeByJobRole(jobRole));
        newProfile.setRoleConfidence(BigDecimal.valueOf(resumeRole.getConfidence()));
        newProfile.setPromptName(PromptUtil.getResumeAnalysisPromptName(jobRole));
        newProfile.setScoreDetail(toJson(aiResult.getScoreDetail()));
        newProfile.setMissingSkills(toJson(aiResult.getMissingSkills()));
        newProfile.setWeakEvidenceItems(toJson(aiResult.getWeakEvidenceItems()));
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
        return responseDto;
    }

    private static StudentCapabilityProfileDTO toDto(StudentCapabilityProfile entity) {
        if (entity == null) {
            return null;
        }
        StudentCapabilityProfileDTO dto = new StudentCapabilityProfileDTO();
        dto.setTargetRoleType(PromptUtil.getPromptDisplayType(entity.getPromptName()));
        dto.setProfessionalSkills(entity.getProfessionalSkills());
        dto.setCertificates(entity.getCertificates());
        dto.setInnovationAbility(entity.getInnovationAbility());
        dto.setLearningAbility(entity.getLearningAbility());
        dto.setPressureResistance(entity.getPressureResistance());
        dto.setCommunicationAbility(entity.getCommunicationAbility());
        dto.setPracticalAbility(entity.getPracticalAbility());
        dto.setCompletenessScore(entity.getCompletenessScore());
        dto.setCompetitivenessScore(entity.getCompetitivenessScore());
        return dto;
    }

    private static StudentCapabilityProfile toEntity(StudentCapabilityProfileDTO dto) {
        if (dto == null) {
            return null;
        }
        StudentCapabilityProfile entity = new StudentCapabilityProfile();
        entity.setProfessionalSkills(dto.getProfessionalSkills());
        entity.setCertificates(dto.getCertificates());
        entity.setInnovationAbility(dto.getInnovationAbility());
        entity.setLearningAbility(dto.getLearningAbility());
        entity.setPressureResistance(dto.getPressureResistance());
        entity.setCommunicationAbility(dto.getCommunicationAbility());
        entity.setPracticalAbility(dto.getPracticalAbility());
        entity.setCompletenessScore(dto.getCompletenessScore());
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
            if (StringUtils.hasText(entity.getMissingSkills())) {
                dto.setMissingSkills(objectMapper.readValue(entity.getMissingSkills(), new TypeReference<List<String>>() {}));
            }
            if (StringUtils.hasText(entity.getWeakEvidenceItems())) {
                dto.setWeakEvidenceItems(objectMapper.readValue(entity.getWeakEvidenceItems(), new TypeReference<List<String>>() {}));
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
     * 补全分数：完整度优先用模型输出，缺失时用分项粗算兜底。
     */
    private void normalizeProfileScores(StudentCapabilityProfileDTO dto) {
        if (dto == null) {
            return;
        }
        if (dto.getCompletenessScore() == null && dto.getScoreDetail() != null
                && dto.getScoreDetail().getContentCompletenessScore() != null) {
            int c = dto.getScoreDetail().getContentCompletenessScore();
            dto.setCompletenessScore(Math.min(100, Math.max(0, c * 10)));
        }
        if (dto.getCompetitivenessScore() == null) {
            dto.setCompetitivenessScore(0);
        }
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
            RoleDetectLLMResult llmResult = chatUtil.chatStructuredOnce(resumeText, PromptUtil.PromptNames.JOB_DETECT,
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
