package com.zdmj.common.ai;

import org.springframework.util.StringUtils;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

public enum ModelEnum {

    QWEN_PLUS("qwen3.6-plus", "通义千问 3.6 Plus",
            "https://dashscope.aliyuncs.com/compatible-mode", "qwen3.6-plus"),
    QWEN_MAX("qwen3.7-max", "通义千问 3.7 Max",
            "https://dashscope.aliyuncs.com/compatible-mode", "qwen3.7-max"),
    DEEPSEEK_FLASH("deepseek-v4-flash", "DeepSeek V4 Flash (2026-04-24)",
            "https://api.deepseek.com", "deepseek-v4-flash"),
    DEEPSEEK_PRO("deepseek-v4-pro", "DeepSeek V4 Pro (2026-04-24)",
            "https://api.deepseek.com", "deepseek-v4-pro");

    private final String code;
    private final String displayName;
    private final String baseUrl;
    private final String apiModelName;

    ModelEnum(String code, String displayName, String baseUrl, String apiModelName) {
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

    public static ModelEnum fromCode(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
        }
        String normalized = modelCode.trim();
        for (ModelEnum value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
    }
}