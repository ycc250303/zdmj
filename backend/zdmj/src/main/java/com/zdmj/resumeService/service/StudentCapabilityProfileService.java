package com.zdmj.resumeService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;

/**
 * 学生就业能力画像服务接口
 */
public interface StudentCapabilityProfileService extends IService<StudentCapabilityProfile> {

    /**
     * 根据当前用户获取画像
     *
     * @return 画像 DTO
     */
    StudentCapabilityProfileResponse getCurrentUserProfile();

    /**
     * 仅查询当前用户画像；若不存在则返回 null
     *
     * @return 画像 DTO 或 null
     */
    StudentCapabilityProfileResponse getCurrentUserProfileOrNull();

    /**
     * 生成学生就业能力画像
     *
     * @param reqDTO 请求参数 (包含 pdfUrl 或 rawText)
     * @return 生成后的画像 DTO
     */
    StudentCapabilityProfileResponse generateProfile(CapabilityProfileGenerateRequest reqDTO);
}
