package com.zdmj.jobService.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.jobService.mapper.JobCapabilityProfileMapper;
import com.zdmj.jobService.mapper.JobCapabilityProfileStructMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.JobCapabilityProfile;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobCapabilityProfileServiceImpl extends ServiceImpl<JobCapabilityProfileMapper, JobCapabilityProfile>
        implements JobCapabilityProfileService {

    private final JobCapabilityProfileStructMapper jobCapabilityProfileStructMapper;
    private final JobService jobService;

    private final ChatUtil chatUtil;

    @Override
    public JobCapabilityProfileDTO getJobCapabilityProfile(Long jobId) {
        // 1. 获取岗位详情
        JobListItemDTO jobDetail = jobService.getDetail(jobId);
        if (jobDetail == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        String jobContext = buildJobContext(jobDetail);

        JobCapabilityProfileDTO aiResult;
        try {
            String response = chatUtil.chat(jobContext, PromptUtil.PromptNames.GENERATE_JOB_CAPABILITY_PROFILE);

            // 清理可能带有的 ```json 标签
            if (response != null && response.startsWith("```json")) {
                response = response.substring(7);
                if (response.endsWith("```")) {
                    response = response.substring(0, response.length() - 3);
                }
            }
            if (response != null && response.startsWith("```")) {
                response = response.substring(3);
                if (response.endsWith("```")) {
                    response = response.substring(0, response.length() - 3);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            aiResult = mapper.readValue(response, JobCapabilityProfileDTO.class);
            if (aiResult == null) {
                throw new BusinessException(500, "大模型返回数据格式异常");
            }

        } catch (Exception e) {
            log.error("生成岗位能力画像失败", e);
            throw new BusinessException(ErrorCode.JOB_CAPABILITY_PROFILE_GENERATION_FAILED);
        }

        // 3. 落库保存或更新
        JobCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<JobCapabilityProfile>()
                        .eq(JobCapabilityProfile::getJobId, jobId));
        JobCapabilityProfile newProfile = jobCapabilityProfileStructMapper.toEntity(aiResult);
        newProfile.setJobId(jobId);
        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }
        return jobCapabilityProfileStructMapper.toDTO(newProfile);
    }

    private String buildJobContext(JobListItemDTO jobDetail) {
        // TODO：后续通过工作内容、岗位要求等进一步细化
        return """
                岗位名称：%s
                岗位薪资：%s
                公司名称：%s
                岗位描述：%s
                """.formatted(jobDetail.getJobName(), jobDetail.getSalary(), jobDetail.getCompanyName(),
                jobDetail.getDescription());
    }
}
