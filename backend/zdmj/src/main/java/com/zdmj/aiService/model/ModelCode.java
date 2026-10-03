package com.zdmj.aiService.model;

import org.springframework.util.StringUtils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 可选对话模型。{@code code} 为配置与接口中的模型码，{@code apiModelName} 为供应商模型名。
 */
public enum ModelCode {

    QWEN_PLUS("qwen3.8-flash", "通义千问 3.8 Flash",
            "https://dashscope.aliyuncs.com/compatible-mode", "qwen3.8-flash"),
    QWEN_MAX("qwen3.8-max", "通义千问 3.8 Max",
            "https://dashscope.aliyuncs.com/compatible-mode", "qwen3.8-max"),
    DEEPSEEK_FLASH("deepseek-v4-flash", "DeepSeek V4 Flash (2026-04-24)",
            "https://api.deepseek.com", "deepseek-v4-flash"),
    DEEPSEEK_PRO("deepseek-v4-pro", "DeepSeek V4 Pro (2026-04-24)",
            "https://api.deepseek.com", "deepseek-v4-pro");

    /** 配置与接口使用的模型码 */
    private final String code;
    /** 展示名称 */
    private final String displayName;
    /** OpenAI 兼容接口的 base URL */
    private final String baseUrl;
    /** 发给供应商的模型名 */
    private final String apiModelName;

    ModelCode(String code, String displayName, String baseUrl, String apiModelName) {
        this.code = code;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.apiModelName = apiModelName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiModelName() {
        return apiModelName;
    }

    /**
     * 按模型码解析目录项。旧码 {@code qwen3.6-plus}、{@code qwen3.7-max} 分别对应 Flash 与 Max。
     *
     * @param modelCode 模型码
     * @return 目录项
     * @throws BusinessException 模型码为空或不在目录内时抛出 {@link ErrorCode#USER_LLM_CONFIG_INVALID}
     */
    public static ModelCode fromCode(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
        }
        String normalized = modelCode.trim();
        if ("qwen3.6-plus".equalsIgnoreCase(normalized)) {
            return QWEN_PLUS;
        }
        if ("qwen3.7-max".equalsIgnoreCase(normalized)) {
            return QWEN_MAX;
        }
        for (ModelCode value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
    }
}
