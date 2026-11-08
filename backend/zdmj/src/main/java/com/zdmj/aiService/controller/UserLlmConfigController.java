package com.zdmj.aiService.controller;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.model.Result;
import com.zdmj.aiService.dto.LlmModelOptionResponse;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConnectionTestRequest;
import com.zdmj.aiService.service.UserLlmConfigService;

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
     * 获取当前用户的模型配置。
     *
     * @return 配置与密钥掩码，没有配置时 configured 为 false
     */     
    @GetMapping
    public Result<UserLlmConfigResponse> getMyConfig() {
        return Result.success(userLlmConfigService.getMyConfig());
    }

    /**
     * 获取可供用户选择的模型目录。
     *
     * @return 模型编号与展示名称
     */
    @GetMapping("/models")
    public Result<List<LlmModelOptionResponse>> listModels() {
        return Result.success(userLlmConfigService.listModels());
    }

    /**
     * 保存当前用户的模型与供应商密钥。
     *
     * @param request 模型编号与明文密钥
     * @return 保存成功响应
     */
    @PutMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> saveMyConfig(@Valid @RequestBody UserLlmConfigRequest request) {
        userLlmConfigService.saveMyConfig(request);
        return Result.success("保存成功", null);
    }

    /**
     * 删除当前用户的模型配置。
     *
     * @return 删除成功响应
     */
    @DeleteMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> deleteMyConfig() {
        userLlmConfigService.deleteMyConfig();
        return Result.success("删除成功", null);
    }

    /**
     * 使用请求体中的模型与 API Key 检查连通性。
     *
     * @param request 模型编号与探测密钥
     * @return 连通性检查成功响应
     */
    @PostMapping("/test")
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> testConnection(@Valid @RequestBody UserLlmConnectionTestRequest request) {
        userLlmConfigService.testConnection(request);
        return Result.success("连通性测试成功", null);
    }
}
