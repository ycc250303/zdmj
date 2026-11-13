package com.zdmj.resumeService.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.model.Result;

import java.util.concurrent.TimeUnit;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     * @param actor 已认证操作者，包含用户编号
     * @return 能力画像信息
     */
    @GetMapping("/current")
    public Result<StudentCapabilityProfileResponse> getCurrentProfile(@Parameter(hidden = true) CurrentActor actor) {
        return Result.success("获取能力画像成功", profileService.getCurrentUserProfile(actor));
    }

    /**
     * 仅查询当前用户能力画像（不存在时返回 null，不触发生成）
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 能力画像信息或 null
     */
    @GetMapping("/current/query")
    public Result<StudentCapabilityProfileResponse> getCurrentProfileOrNull(@Parameter(hidden = true) CurrentActor actor) {
        return Result.success("查询能力画像成功", profileService.getCurrentUserProfileOrNull(actor));
    }

    /**
     * 生成能力画像并返回结果。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param reqDTO 生成参数
     * @return 生成的能力画像
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<StudentCapabilityProfileResponse> generateProfile(@Parameter(hidden = true) CurrentActor actor, @Validated @RequestBody CapabilityProfileGenerateRequest reqDTO) {
        log.info("生成学生能力画像");
        return Result.success("生成能力画像成功", profileService.generateProfile(actor, reqDTO));
    }

    /**
     * 从上传文件提取文本并生成能力画像。文件只在本次请求中读取，不写入对象存储。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param file 简历文件
     * @return 生成的能力画像
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<StudentCapabilityProfileResponse> generateProfileFromFile(@Parameter(hidden = true) CurrentActor actor,
            @RequestParam("file") MultipartFile file) {
        log.info("从上传文件生成学生能力画像");
        return Result.success("生成能力画像成功", profileService.generateProfile(actor, file));
    }
}
