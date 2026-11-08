package com.zdmj.aiService.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 使用请求中的模型与密钥进行连通性检查，不保存配置。 */
@Data
public class UserLlmConnectionTestRequest {

    /** 模型目录编号，不能为空，须能够解析为受支持的模型。 */
    @NotBlank(message = "模型不能为空")
    private String modelCode;

    /** 本次探测使用的供应商明文密钥，不能为空。 */
    @NotBlank(message = "API Key 不能为空")
    @lombok.ToString.Exclude
    private String apiKey;
}
