package com.zdmj.common.ai;

import org.junit.jupiter.api.Test;

import com.zdmj.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelEnumTest {

    @Test
    void fromCode_resolvesQwenAndDeepSeek() {
        assertEquals(ModelEnum.QWEN_PLUS, ModelEnum.fromCode("qwen3.6-plus"));
        assertEquals(ModelEnum.QWEN_MAX, ModelEnum.fromCode("QWEN3.7-MAX"));
        assertEquals(ModelEnum.DEEPSEEK_FLASH, ModelEnum.fromCode("deepseek-v4-flash"));
        assertEquals(ModelEnum.DEEPSEEK_PRO, ModelEnum.fromCode("DEEPSEEK-V4-PRO"));
    }

    @Test
    void qwenUsesDashScopeBaseUrl() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode", ModelEnum.QWEN_PLUS.baseUrl());
        assertEquals("qwen3.6-plus", ModelEnum.QWEN_PLUS.apiModelName());
        assertEquals("qwen3.7-max", ModelEnum.QWEN_MAX.apiModelName());
    }

    @Test
    void deepSeekUsesOfficialBaseUrl() {
        assertEquals("https://api.deepseek.com", ModelEnum.DEEPSEEK_FLASH.baseUrl());
        assertEquals("deepseek-v4-flash", ModelEnum.DEEPSEEK_FLASH.apiModelName());
        assertEquals("deepseek-v4-pro", ModelEnum.DEEPSEEK_PRO.apiModelName());
    }

    @Test
    void fromCode_rejectsLegacyModelCodes() {
        assertThrows(BusinessException.class, () -> ModelEnum.fromCode("deepseek-flash"));
        assertThrows(BusinessException.class, () -> ModelEnum.fromCode("deepseek-pro"));
    }
}
