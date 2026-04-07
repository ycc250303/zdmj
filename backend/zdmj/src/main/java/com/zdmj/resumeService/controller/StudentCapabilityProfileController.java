package com.zdmj.resumeService.controller;

import com.zdmj.common.model.Result;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateReqDTO;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 学生就业能力画像控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/capability-profile")
public class StudentCapabilityProfileController {

    private final StudentCapabilityProfileService profileService;

    /**
     * 获取当前用户的能力画像
     *
     * @return 能力画像信息
     */
    @GetMapping("/current")
    public Result<StudentCapabilityProfileDTO> getCurrentProfile() {
        return Result.success("获取能力画像成功", profileService.getCurrentUserProfile());
    }

    /**
     * 生成能力画像（支持从 PDF 解析或文本直接生成）
     *
     * @param reqDTO 生成参数
     * @return 生成的能力画像
     */
    @PostMapping("/generate")
    public Result<StudentCapabilityProfileDTO> generateProfile(@Validated @RequestBody CapabilityProfileGenerateReqDTO reqDTO) {
        log.info("开始生成学生能力画像");
        StudentCapabilityProfileDTO profileDTO = profileService.generateProfile(reqDTO);
        return Result.success("生成能力画像成功", profileDTO);
    }
}
