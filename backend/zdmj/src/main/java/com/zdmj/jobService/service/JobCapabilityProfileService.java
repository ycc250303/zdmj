package com.zdmj.jobService.service;

import com.zdmj.jobService.dto.JobCapabilityProfileResponse;

public interface JobCapabilityProfileService {
    /**
     * 为当前用户生成岗位能力画像（已有则覆盖本人旧行）
     *
     * @param jobId 岗位ID
     * @return 岗位能力画像
     */
    JobCapabilityProfileResponse getJobCapabilityProfile(Long jobId);

    /**
     * 仅查询当前用户对该岗的能力画像；未登录或不存在则返回 null
     *
     * @param jobId 岗位ID
     * @return 岗位能力画像或 null
     */
    JobCapabilityProfileResponse getJobCapabilityProfileOrNull(Long jobId);
}
