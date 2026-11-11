package com.zdmj.aiService.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.aiService.service.UserLlmConfigService;
import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.Result;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/llm-config")
@RequiredArgsConstructor
@Tag(name = "用户大模型配置", description = "用户自选模型与 API Key")
public class UserLlmConfigController {

    private final UserLlmConfigService userLlmConfigService;

    /**
     * 获取当前用户的模型配置及可选模型目录。
     *
     * @return 配置、密钥掩码与目录，没有配置时 configured 为 false
     */
    @GetMapping
    public Result<UserLlmConfigResponse> getMyConfig() {
        return Result.success(userLlmConfigService.getMyConfig(currentActor()));
    }

    /**
     * 校验供应商连通性后保存当前用户的模型与密钥。
     *
     * @param request 模型编号与明文密钥
     * @return 保存成功响应
     */
    @PutMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> saveMyConfig(@Valid @RequestBody UserLlmConfigRequest request) {
        userLlmConfigService.saveMyConfig(currentActor(), request);
        return Result.success("校验通过并已保存", null);
    }

    /**
     * 删除当前用户的模型配置。
     *
     * @return 删除成功响应
     */
    @DeleteMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> deleteMyConfig() {
        userLlmConfigService.deleteMyConfig(currentActor());
        return Result.success("删除成功", null);
    }

    private static CurrentActor currentActor() {
        return CurrentActor.of(UserHolder.requireUserId());
    }
}
