package com.zdmj.common.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.UserApiKeyCipher;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UserLlmRouterTest {

    @Mock
    private UserLlmConfigMapper userLlmConfigMapper;
    @Mock
    private UserApiKeyCipher userApiKeyCipher;
    @Mock
    private ChatMemory chatMemory;

    private UserLlmRouter router;

    @BeforeEach
    void setUp() {
        router = new UserLlmRouter(userLlmConfigMapper, userApiKeyCipher, chatMemory);
        ReflectionTestUtils.setField(router, "platformFallbackEnabled", true);
        ReflectionTestUtils.setField(router, "platformBaseUrl",
                "https://dashscope.aliyuncs.com/compatible-mode");
        ReflectionTestUtils.setField(router, "platformApiKey", "sk-platform");
        ReflectionTestUtils.setField(router, "platformModel", "qwen3.8-max");
        ReflectionTestUtils.setField(router, "deepseekApiKey", "sk-deepseek");
    }

    @Test
    void resolveResumeImportModel_whenDeepSeekConfigured_shouldIgnorePlatformMax() {
        assertEquals(ModelEnum.DEEPSEEK_FLASH, router.resolveResumeImportModel());
    }

    @Test
    void resolveResumeImportModel_whenNoDeepSeek_shouldFallbackToFlashNotMax() {
        ReflectionTestUtils.setField(router, "deepseekApiKey", "  ");
        assertEquals(ModelEnum.QWEN_PLUS, router.resolveResumeImportModel());
        assertEquals("qwen3.8-flash", router.resolveResumeImportModel().code());
    }

    @Test
    void resolveResumeImportModel_whenNoKeys_shouldThrow() {
        ReflectionTestUtils.setField(router, "deepseekApiKey", "");
        ReflectionTestUtils.setField(router, "platformApiKey", "");
        BusinessException ex = assertThrows(BusinessException.class, router::resolveResumeImportModel);
        assertEquals(ErrorCode.USER_LLM_NOT_CONFIGURED.getCode(), ex.getCode());
    }

    @Test
    void listModelOptions_shouldExposeFlashAndMax() {
        assertEquals("qwen3.8-flash", router.listModelOptions().get(0).code());
        assertEquals("qwen3.8-max", router.listModelOptions().get(1).code());
    }
}
