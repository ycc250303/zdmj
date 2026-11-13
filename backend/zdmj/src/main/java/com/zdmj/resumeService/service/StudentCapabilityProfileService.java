package com.zdmj.resumeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import org.springframework.web.multipart.MultipartFile;

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
     * 根据纯文本生成学生就业能力画像。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param reqDTO 纯文本请求
     * @return 生成后的画像
     * @throws com.zdmj.common.exception.BusinessException 文本为空或生成失败
     */
    StudentCapabilityProfileResponse generateProfile(CurrentActor actor, CapabilityProfileGenerateRequest reqDTO);

    /**
     * 从上传文件提取文本后生成画像，不保存文件。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param file 非空简历文件
     * @return 生成后的画像
     * @throws com.zdmj.common.exception.BusinessException 文件为空、无法读取、提取文本为空或生成失败
     */
    StudentCapabilityProfileResponse generateProfile(CurrentActor actor, MultipartFile file);
}
