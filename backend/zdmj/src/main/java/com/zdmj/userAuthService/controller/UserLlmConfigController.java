package com.zdmj.userAuthService.controller;

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
import com.zdmj.userAuthService.dto.LlmModelOptionDTO;
import com.zdmj.userAuthService.dto.UserLlmConfigDTO;
import com.zdmj.userAuthService.dto.UserLlmConfigRequest;
import com.zdmj.userAuthService.dto.UserLlmConnectionTestRequest;
import com.zdmj.userAuthService.service.UserLlmConfigService;

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
     * 获取用户当前配置
     * @return
     */     
    @GetMapping
    public Result<UserLlmConfigDTO> getMyConfig() {
        return Result.success(userLlmConfigService.getMyConfig());
    }

    /**
     * 获取模型列表
     * @return
     */
    @GetMapping("/models")
    public Result<List<LlmModelOptionDTO>> listModels() {
        return Result.success(userLlmConfigService.listModels());
    }

    /**
     * 保存用户当前配置
     * @param request
     * @return
     */
    @PutMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> saveMyConfig(@Valid @RequestBody UserLlmConfigRequest request) {
        userLlmConfigService.saveMyConfig(request);
        return Result.success("保存成功", null);
    }

    /**
     * 删除用户当前配置
     * @return
     */
    @DeleteMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> deleteMyConfig() {
        userLlmConfigService.deleteMyConfig();
        return Result.success("删除成功", null);
    }

    /**
     * 使用请求体中的模型与 API Key 测试连通性（不读库）
     */
    @PostMapping("/test")
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> testConnection(@Valid @RequestBody UserLlmConnectionTestRequest request) {
        userLlmConfigService.testConnection(request);
        return Result.success("连通性测试成功", null);
    }
}