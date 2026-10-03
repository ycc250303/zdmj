package com.zdmj.aiService.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

class ModelCodeTest {

    @Test
    void fromCode_shouldResolveCurrentQwenCodes() {
        assertEquals(ModelCode.QWEN_PLUS, ModelCode.fromCode("qwen3.8-flash"));
        assertEquals(ModelCode.QWEN_MAX, ModelCode.fromCode("qwen3.8-max"));
        assertEquals("qwen3.8-flash", ModelCode.QWEN_PLUS.apiModelName());
        assertEquals("qwen3.8-max", ModelCode.QWEN_MAX.apiModelName());
    }

    @Test
    void fromCode_shouldMapLegacyQwenCodes() {
        assertEquals(ModelCode.QWEN_PLUS, ModelCode.fromCode("qwen3.6-plus"));
        assertEquals(ModelCode.QWEN_MAX, ModelCode.fromCode("qwen3.7-max"));
        assertEquals("qwen3.8-flash", ModelCode.fromCode("QWEN3.6-PLUS").code());
        assertEquals("qwen3.8-max", ModelCode.fromCode(" qwen3.7-max ").code());
    }

    @Test
    void fromCode_whenBlankOrUnknown_shouldThrow() {
        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> ModelCode.fromCode(null)).getCode());
        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> ModelCode.fromCode("  ")).getCode());
        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> ModelCode.fromCode("qwen-3.8-flash")).getCode());
    }
}
