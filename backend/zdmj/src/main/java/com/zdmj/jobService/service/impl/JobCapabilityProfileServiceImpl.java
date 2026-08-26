package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.ai.PromptUtil.JobRole;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
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
    public JobCapabilityProfileResponse getJobCapabilityProfile(Long jobId) {
        JobListItemResponse jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }

        String jobContext = JobAnalysisSupport.buildJobContext(
                jobDetail,
                "这是待分析的岗位信息（面向求职者输出岗位要求画像）：");
        JobRole role = JobAnalysisSupport.detectRole(
                jobContext, chatUtil, KEYWORDS, KEYWORD_DIRECT_HIT_THRESHOLD, log);
        log.info("岗位类型识别: role={}", role);
        String promptName = PromptUtil.getJobRequirementPromptName(role);
        log.info("使用提示词: {}", promptName);

        JobCapabilityProfileResponse aiResult;
        try {
            aiResult = chatUtil.chatStructuredOnce(jobContext, promptName, null, JobCapabilityProfileResponse.class);
        } catch (Exception e) {
            log.error("岗位要求画像生成失败，role={}, prompt={}", role, promptName, e);
            throw new BusinessException(ErrorCode.JOB_CAPABILITY_PROFILE_GENERATION_FAILED);
        }

        JobCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<JobCapabilityProfile>().eq(JobCapabilityProfile::getJobId, jobId));

        JobCapabilityProfile newProfile = toEntity(aiResult);
        newProfile.setJobId(jobId);
        newProfile.setRoleConfidence(BigDecimal.valueOf(
                JobAnalysisSupport.estimateRoleConfidence(role, jobContext, KEYWORDS)));
        newProfile.setPromptName(promptName);
        newProfile.setTargetRoleType(PromptUtil.getPromptDisplayType(promptName));
        newProfile.setStrengths(JobAnalysisSupport.toJson(
                aiResult.getStrengths(), objectMapper, log, "岗位画像 JSON 序列化失败，字段将置空"));
        newProfile.setMissingSkills(JobAnalysisSupport.toJson(
                aiResult.getMissingSkills(), objectMapper, log, "岗位画像 JSON 序列化失败，字段将置空"));
        newProfile.setWeakEvidenceItems(JobAnalysisSupport.toJson(
                aiResult.getWeakEvidenceItems(), objectMapper, log, "岗位画像 JSON 序列化失败，字段将置空"));

        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }

        JobCapabilityProfileResponse responseDto = toDto(newProfile);
        hydrateDtoFromEntity(newProfile, responseDto);
        return responseDto;
    }

    @Override
    public JobCapabilityProfileResponse getJobCapabilityProfileOrNull(Long jobId) {
        JobListItemResponse jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        JobCapabilityProfile profile = getOne(
                new LambdaQueryWrapper<JobCapabilityProfile>().eq(JobCapabilityProfile::getJobId, jobId));
        if (profile == null) {
            return null;
        }
        JobCapabilityProfileResponse dto = toDto(profile);
        hydrateDtoFromEntity(profile, dto);
        return dto;
    }

    private static JobCapabilityProfileResponse toDto(JobCapabilityProfile entity) {
        if (entity == null) {
            return null;
        }
        JobCapabilityProfileResponse dto = new JobCapabilityProfileResponse();
        dto.setTargetRoleType(StringUtils.hasText(entity.getTargetRoleType()) ? entity.getTargetRoleType()
                : PromptUtil.getPromptDisplayType(entity.getPromptName()));
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

    private static JobCapabilityProfile toEntity(JobCapabilityProfileResponse dto) {
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

    private void hydrateDtoFromEntity(JobCapabilityProfile entity, JobCapabilityProfileResponse dto) {
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

}
