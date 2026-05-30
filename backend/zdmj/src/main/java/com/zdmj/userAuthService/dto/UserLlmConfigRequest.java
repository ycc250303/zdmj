package com.zdmj.userAuthService.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLlmConfigRequest {

    @NotBlank(message = "模型不能为空")
    private String modelCode;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;
}