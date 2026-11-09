package com.zdmj.aiService.support;

import java.util.Objects;

import com.zdmj.aiService.enums.ModelCode;

/**
 * 已解析的用户配置。密钥用于构建客户端或生成展示掩码，明文禁止写入日志、响应或缓存。
 *
 * @param model 用户选择的模型，不能为空
 * @param apiKey 解密后的非空白密钥，构造时去除首尾空白
 */
public record ResolvedModelConfiguration(ModelCode model, String apiKey) {

    public ResolvedModelConfiguration {
        Objects.requireNonNull(model, "模型不能为空");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("模型密钥不能为空");
        }
        apiKey = apiKey.trim();
    }

    @Override
    public String toString() {
        return "ResolvedModelConfiguration[model=" + model + "]";
    }
}
