package com.zdmj.aiService.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 用户保存模型配置的请求，密钥只用于加密存储。 */
@Data
public class UserLlmConfigRequest {

    /** 模型目录编号，不能为空，须能够解析为受支持的模型。 */
    @NotBlank(message = "模型不能为空")
    private String modelCode;

    /** 供应商明文密钥，不能为空，保存时去除首尾空白。 */
    @NotBlank(message = "API Key 不能为空")
    @lombok.ToString.Exclude
    private String apiKey;
}
