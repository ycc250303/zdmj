package com.zdmj.resumeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;

/**
 * 学生就业能力画像服务接口
 */
public interface StudentCapabilityProfileService {

    /**
     * 根据当前用户获取画像
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 画像 DTO
     */
    StudentCapabilityProfileResponse getCurrentUserProfile(CurrentActor actor);

    /**
     * 仅查询当前用户画像；若不存在则返回 null
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 画像 DTO 或 null
     */
    StudentCapabilityProfileResponse getCurrentUserProfileOrNull(CurrentActor actor);

    /**
     * 生成学生就业能力画像
     *
     * @param actor 已认证操作者，包含用户编号
     * @param reqDTO 请求参数 (包含 pdfUrl 或 rawText)
     * @return 生成后的画像 DTO
     */
    StudentCapabilityProfileResponse generateProfile(CurrentActor actor, CapabilityProfileGenerateRequest reqDTO);
}
