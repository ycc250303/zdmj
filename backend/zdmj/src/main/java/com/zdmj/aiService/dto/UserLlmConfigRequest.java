package com.zdmj.aiService.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 校验并保存模型配置的请求，密钥用于本次探测及加密存储。
 *
 * @param modelCode 模型目录编号，不能为空，须能够解析为受支持的模型。
 * @param apiKey 供应商明文密钥，不能为空，保存和探测时去除首尾空白。
 */
public record UserLlmConfigRequest(
        @NotBlank(message = "模型不能为空")
        String modelCode,
        @NotBlank(message = "API Key 不能为空")
        String apiKey) {

    @Override
    public String toString() {
        return "UserLlmConfigRequest[modelCode=" + modelCode + "]";
    }
}
