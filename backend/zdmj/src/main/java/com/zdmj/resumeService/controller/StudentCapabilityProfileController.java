package com.zdmj.resumeService.controller;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.ai.LlmRateLimits;
import com.zdmj.common.model.Result;

import java.util.concurrent.TimeUnit;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "能力画像", description = "学生就业能力画像生成与查询")
public class StudentCapabilityProfileController {

    private final StudentCapabilityProfileService profileService;

    /**
     * 获取当前用户的能力画像
     *
     * @return 能力画像信息
     */
    @GetMapping("/current")
    public Result<StudentCapabilityProfileResponse> getCurrentProfile() {
        return Result.success("获取能力画像成功", profileService.getCurrentUserProfile());
    }

    /**
     * 仅查询当前用户能力画像（不存在时返回 null，不触发生成）
     *
     * @return 能力画像信息或 null
     */
    @GetMapping("/current/query")
    public Result<StudentCapabilityProfileResponse> getCurrentProfileOrNull() {
        return Result.success("查询能力画像成功", profileService.getCurrentUserProfileOrNull());
    }

    /**
     * 生成能力画像（支持从 PDF 解析或文本直接生成）
     *
     * @param reqDTO 生成参数
     * @return 生成的能力画像
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = LlmRateLimits.CAPABILITY_PROFILE_GENERATE_PER_MIN,
            interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/generate")
    public Result<StudentCapabilityProfileResponse> generateProfile(@Validated @RequestBody CapabilityProfileGenerateRequest reqDTO) {
        log.info("开始生成学生能力画像");
        StudentCapabilityProfileResponse profileDTO = profileService.generateProfile(reqDTO);
        return Result.success("生成能力画像成功", profileDTO);
    }
}
