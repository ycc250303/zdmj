package com.zdmj.aiService.provider;

import com.zdmj.aiService.model.ModelCode;

/**
 * 一次模型调用所需的用户配置。密钥只在构建客户端时使用，禁止写入日志、响应或缓存。
 *
 * @param model  用户选择的模型；未配置时为空
 * @param apiKey 解密后的用户密钥；未配置时为空
 */
public record ResolvedModelConfiguration(ModelCode model, String apiKey) {

    /**
     * 用户没有可用的自配模型。
     *
     * @return 空配置
     */
    public static ResolvedModelConfiguration absent() {
        return new ResolvedModelConfiguration(null, null);
    }

    /**
     * 是否包含可用的模型与密钥。
     *
     * @return 两者都有内容时为 true
     */
    public boolean configured() {
        return model != null && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "ResolvedModelConfiguration[model=" + model + ", configured=" + configured() + "]";
    }
}
