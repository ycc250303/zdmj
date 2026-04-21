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
import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.JobCapabilityProfile;
import com.zdmj.jobService.mapper.JobCapabilityProfileMapper;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobCapabilityProfileServiceImpl extends ServiceImpl<JobCapabilityProfileMapper, JobCapabilityProfile>
        implements JobCapabilityProfileService {

    /** 关键词命中达到该分数则直接采用规则结果，不调 LLM 分类 */
    private static final int KEYWORD_DIRECT_HIT_THRESHOLD = 4;

    private final JobService jobService;
    private final ChatUtil chatUtil;
    private final ObjectMapper objectMapper;

    private static final Map<JobRole, List<String>> KEYWORDS = Map.of(
            JobRole.JAVA, List.of("java", "spring", "spring boot", "mybatis", "mysql", "redis", "jvm"),
            JobRole.FRONTEND,
            List.of("react", "vue", "typescript", "javascript", "webpack", "vite", "css", "html"),
            JobRole.CPP, List.of("c++", "cpp", "stl", "cmake", "gdb", "linux", "多线程", "内存"),
            JobRole.SOFTWARE_TEST,
            List.of("测试", "test case", "pytest", "selenium", "jmeter", "postman", "缺陷"));

    @Override
    public JobCapabilityProfileDTO getJobCapabilityProfile(Long jobId) {
        JobListItemDTO jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }

        String jobContext = buildJobContext(jobDetail);
        JobRole role = detectRole(jobContext);
        log.info("岗位类型识别: role={}", role);
        String promptName = PromptUtil.getJobRequirementPromptName(role);
        log.info("使用提示词: {}", promptName);

        JobCapabilityProfileDTO aiResult;
        try {
            aiResult = chatUtil.chatStructuredOnce(jobContext, promptName, null, JobCapabilityProfileDTO.class);
        } catch (Exception e) {
            log.error("岗位要求画像生成失败，role={}, prompt={}", role, promptName, e);
            throw new BusinessException(ErrorCode.JOB_CAPABILITY_PROFILE_GENERATION_FAILED);
        }

        JobCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<JobCapabilityProfile>().eq(JobCapabilityProfile::getJobId, jobId));

        JobCapabilityProfile newProfile = toEntity(aiResult);
        newProfile.setJobId(jobId);
        newProfile.setTargetRoleCode(PromptUtil.getRoleCodeByJobRole(role));
        newProfile.setRoleConfidence(BigDecimal.valueOf(estimateRoleConfidence(role, jobContext)));
        newProfile.setPromptName(promptName);
        newProfile.setStrengths(toJson(aiResult.getStrengths()));
        newProfile.setMissingSkills(toJson(aiResult.getMissingSkills()));
        newProfile.setWeakEvidenceItems(toJson(aiResult.getWeakEvidenceItems()));

        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }

        JobCapabilityProfileDTO responseDto = toDto(newProfile);
        hydrateDtoFromEntity(newProfile, responseDto);
        return responseDto;
    }

    @Override
    public JobCapabilityProfileDTO getJobCapabilityProfileOrNull(Long jobId) {
        JobListItemDTO jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        JobCapabilityProfile profile = getOne(
                new LambdaQueryWrapper<JobCapabilityProfile>().eq(JobCapabilityProfile::getJobId, jobId));
        if (profile == null) {
            return null;
        }
        JobCapabilityProfileDTO dto = toDto(profile);
        hydrateDtoFromEntity(profile, dto);
        return dto;
    }

    /**
     * 岗位类型识别
     * @param text 岗位文本
     * @return 岗位类型
     */
    private JobRole detectRole(String text) {
        if (!StringUtils.hasText(text)) {
            return JobRole.UNKNOWN;
        }
        String lower = text.toLowerCase();
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

    private static JobCapabilityProfileDTO toDto(JobCapabilityProfile entity) {
        if (entity == null) {
            return null;
        }
        JobCapabilityProfileDTO dto = new JobCapabilityProfileDTO();
        dto.setTargetRoleType(PromptUtil.getPromptDisplayType(entity.getPromptName()));
        dto.setProfessionalSkills(entity.getProfessionalSkills());
        dto.setCertificates(entity.getCertificates());
        dto.setInnovationAbility(entity.getInnovationAbility());
        dto.setLearningAbility(entity.getLearningAbility());
        dto.setPressureResistance(entity.getPressureResistance());
        dto.setCommunicationAbility(entity.getCommunicationAbility());
        dto.setPracticalAbility(entity.getPracticalAbility());
        dto.setSummary(entity.getSummary());
        return dto;
    }

    private static JobCapabilityProfile toEntity(JobCapabilityProfileDTO dto) {
        if (dto == null) {
            return null;
        }
        JobCapabilityProfile entity = new JobCapabilityProfile();
        entity.setProfessionalSkills(dto.getProfessionalSkills());
        entity.setCertificates(dto.getCertificates());
        entity.setInnovationAbility(dto.getInnovationAbility());
        entity.setLearningAbility(dto.getLearningAbility());
        entity.setPressureResistance(dto.getPressureResistance());
        entity.setCommunicationAbility(dto.getCommunicationAbility());
        entity.setPracticalAbility(dto.getPracticalAbility());
        entity.setSummary(dto.getSummary());
        return entity;
    }

    private void hydrateDtoFromEntity(JobCapabilityProfile entity, JobCapabilityProfileDTO dto) {
        if (entity == null || dto == null) {
            return;
        }
        try {
            if (StringUtils.hasText(entity.getStrengths())) {
                dto.setStrengths(objectMapper.readValue(entity.getStrengths(), new TypeReference<List<String>>() {
                }));
            }
            if (StringUtils.hasText(entity.getMissingSkills())) {
                dto.setMissingSkills(objectMapper.readValue(entity.getMissingSkills(), new TypeReference<List<String>>() {
                }));
            }
            if (StringUtils.hasText(entity.getWeakEvidenceItems())) {
                dto.setWeakEvidenceItems(
                        objectMapper.readValue(entity.getWeakEvidenceItems(), new TypeReference<List<String>>() {
                        }));
            }
        } catch (Exception e) {
            log.warn("反序列化岗位画像 JSON 失败: {}", e.getMessage());
        }
    }

    private String buildJobContext(JobListItemDTO job) {
        return """
                这是待分析的岗位信息（面向求职者输出岗位要求画像）：
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
        return values.stream().filter(StringUtils::hasText).map(String::trim).reduce((a, b) -> a + "；" + b)
                .orElse("未提供");
    }

    private static String valueOrNA(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未提供";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("岗位画像 JSON 序列化失败，字段将置空: {}", e.getMessage());
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
