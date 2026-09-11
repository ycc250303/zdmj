package com.zdmj.resumeService.controller;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.async.AsyncTaskDTO;
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
     * 入队生成能力画像，立即返回任务；完成后查 {@code GET /capability-profile/current/query}。
     *
     * @param reqDTO 生成参数
     * @return 异步任务
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/generate")
    public Result<AsyncTaskDTO> generateProfile(@Validated @RequestBody CapabilityProfileGenerateRequest reqDTO) {
        log.info("入队生成学生能力画像");
        return Result.success("已提交能力画像生成任务", profileService.enqueueGenerate(reqDTO));
    }
}
