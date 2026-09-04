package com.zdmj.common.ai;

import org.junit.jupiter.api.Test;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelEnumTest {

    @Test
    void fromCode_shouldResolveCurrentQwenCodes() {
        assertEquals(ModelEnum.QWEN_PLUS, ModelEnum.fromCode("qwen3.8-flash"));
        assertEquals(ModelEnum.QWEN_MAX, ModelEnum.fromCode("qwen3.8-max"));
        assertEquals("qwen3.8-flash", ModelEnum.QWEN_PLUS.apiModelName());
        assertEquals("qwen3.8-max", ModelEnum.QWEN_MAX.apiModelName());
    }

    @Test
    void fromCode_shouldMapLegacyQwenCodes() {
        assertEquals(ModelEnum.QWEN_PLUS, ModelEnum.fromCode("qwen3.6-plus"));
        assertEquals(ModelEnum.QWEN_MAX, ModelEnum.fromCode("qwen3.7-max"));
        assertEquals("qwen3.8-flash", ModelEnum.fromCode("QWEN3.6-PLUS").code());
        assertEquals("qwen3.8-max", ModelEnum.fromCode(" qwen3.7-max ").code());
    }

    @Test
    void fromCode_whenBlankOrUnknown_shouldThrow() {
        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> ModelEnum.fromCode(null)).getCode());
        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> ModelEnum.fromCode("  ")).getCode());
        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> ModelEnum.fromCode("qwen-3.8-flash")).getCode());
    }
}
