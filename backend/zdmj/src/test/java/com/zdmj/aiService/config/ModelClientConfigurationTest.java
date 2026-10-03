package com.zdmj.aiService.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.zdmj.aiService.model.ModelCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

class ModelClientConfigurationTest {

    @Test
    void resumeImportModel_whenDeepSeekConfigured_shouldIgnorePlatformMax() {
        ModelClientConfiguration clients = clients("sk-deepseek", "sk-platform");
        assertEquals(ModelCode.DEEPSEEK_FLASH, clients.resumeImportModel());
    }

    @Test
    void resumeImportModel_whenNoDeepSeek_shouldFallbackToFlashNotMax() {
        ModelClientConfiguration clients = clients("  ", "sk-platform");
        assertEquals(ModelCode.QWEN_PLUS, clients.resumeImportModel());
        assertEquals("qwen3.8-flash", clients.resumeImportModel().code());
    }

    @Test
    void resumeImportModel_whenNoKeys_shouldThrow() {
        ModelClientConfiguration clients = clients("", "");
        BusinessException ex = assertThrows(BusinessException.class, clients::resumeImportModel);
        assertEquals(ErrorCode.USER_LLM_NOT_CONFIGURED.getCode(), ex.getCode());
    }

    private static ModelClientConfiguration clients(String deepseekApiKey, String platformApiKey) {
        return new ModelClientConfiguration(
                true,
                "https://dashscope.aliyuncs.com/compatible-mode",
                platformApiKey,
                "qwen3.8-max",
                deepseekApiKey);
    }
}
