package com.zdmj.jobService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.entity.JobCapabilityProfile;

public interface JobCapabilityProfileService extends IService<JobCapabilityProfile> {
    /**
     * 根据岗位ID获取岗位能力画像(如果没有则生成)
     *
     * @param jobId 岗位ID
     * @return 岗位能力画像
     */
    JobCapabilityProfileDTO getJobCapabilityProfile(Long jobId);
}
