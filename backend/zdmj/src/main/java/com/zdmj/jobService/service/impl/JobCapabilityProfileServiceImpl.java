package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.JobRole;
import com.zdmj.common.ai.JobRoleDetector;
import com.zdmj.common.ai.PromptScenario;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.entity.JobCapabilityProfile;
import com.zdmj.jobService.mapper.JobCapabilityProfileMapper;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobCapabilityProfileServiceImpl extends ServiceImpl<JobCapabilityProfileMapper, JobCapabilityProfile>
        implements JobCapabilityProfileService {

    private final JobService jobService;
    private final ChatUtil chatUtil;
    private final PromptUtil promptUtil;

    @Override
    public JobCapabilityProfileResponse getJobCapabilityProfile(Long jobId) {
        Long userId = UserHolder.requireUserId();
        JobListItemResponse jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }

        String jobContext = JobAnalysisSupport.buildJobContext(
                jobDetail,
                "这是待分析的岗位信息（面向求职者输出岗位要求画像）：");
        JobRoleDetector.DetectResult detected = JobRoleDetector.detect(userId, jobContext, chatUtil, log);
        JobRole role = detected.role();
        log.info("岗位类型识别: role={}", role);
        String promptName = promptUtil.resolve(PromptScenario.JOB_REQUIREMENT, role);
        log.info("使用提示词: {}", promptName);

        JobCapabilityProfileResponse aiResult;
        try {
            aiResult = chatUtil.chatStructuredOnce(userId, jobContext, promptName, null, JobCapabilityProfileResponse.class);
        } catch (Exception e) {
            log.error("岗位要求画像生成失败，role={}, prompt={}", role, promptName, e);
            throw new BusinessException(ErrorCode.JOB_CAPABILITY_PROFILE_GENERATION_FAILED);
        }

        JobCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<JobCapabilityProfile>().eq(JobCapabilityProfile::getJobId, jobId));

        JobCapabilityProfile newProfile = toEntity(aiResult);
        newProfile.setJobId(jobId);
        newProfile.setRoleConfidence(BigDecimal.valueOf(detected.confidence()));
        newProfile.setPromptName(promptName);
        newProfile.setTargetRoleType(role.slug());

        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }

        return toDto(newProfile);
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
        return toDto(profile);
    }

    private static JobCapabilityProfileResponse toDto(JobCapabilityProfile entity) {
        if (entity == null) {
            return null;
        }
        JobCapabilityProfileResponse dto = new JobCapabilityProfileResponse();
        dto.setTargetRoleType(StringUtils.hasText(entity.getTargetRoleType()) ? entity.getTargetRoleType()
                : JobRole.fromPromptName(entity.getPromptName()).slug());
        dto.setRoleConfidence(entity.getRoleConfidence());
        dto.setProfessionalSkills(entity.getProfessionalSkills());
        dto.setCertificates(entity.getCertificates());
        dto.setInnovationAbility(entity.getInnovationAbility());
        dto.setLearningAbility(entity.getLearningAbility());
        dto.setPressureResistance(entity.getPressureResistance());
        dto.setCommunicationAbility(entity.getCommunicationAbility());
        dto.setPracticalAbility(entity.getPracticalAbility());
        dto.setSummary(entity.getSummary());
        dto.setStrengths(entity.getStrengths());
        dto.setMissingSkills(entity.getMissingSkills());
        dto.setWeakEvidenceItems(entity.getWeakEvidenceItems());
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
        entity.setStrengths(dto.getStrengths());
        entity.setMissingSkills(dto.getMissingSkills());
        entity.setWeakEvidenceItems(dto.getWeakEvidenceItems());
        return entity;
    }

}
