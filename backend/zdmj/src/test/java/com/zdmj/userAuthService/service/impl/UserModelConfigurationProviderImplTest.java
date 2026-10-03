package com.zdmj.userAuthService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zdmj.aiService.model.ModelCode;
import com.zdmj.aiService.provider.ResolvedModelConfiguration;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.entity.UserLlmConfig;
import com.zdmj.userAuthService.llm.UserApiKeyCipher;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

@ExtendWith(MockitoExtension.class)
class UserModelConfigurationProviderImplTest {

    @Mock
    private UserLlmConfigMapper userLlmConfigMapper;
    @Mock
    private UserApiKeyCipher userApiKeyCipher;

    private UserModelConfigurationProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new UserModelConfigurationProviderImpl(userLlmConfigMapper, userApiKeyCipher);
    }

    @Test
    void resolve_whenActorMissing_shouldReturnAbsentWithoutReadingStorage() {
        assertFalse(provider.resolve(null).configured());
        assertFalse(provider.resolve(CurrentActor.of(null)).configured());

        verifyNoInteractions(userLlmConfigMapper, userApiKeyCipher);
    }

    @Test
    void resolve_whenConfigMissing_shouldReturnAbsent() {
        when(userLlmConfigMapper.selectById(7L)).thenReturn(null);

        ResolvedModelConfiguration resolved = provider.resolve(CurrentActor.of(7L));

        assertFalse(resolved.configured());
        verify(userApiKeyCipher, never()).decrypt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolve_whenConfigExists_shouldParseModelAndTrimDecryptedKey() {
        UserLlmConfig config = config("qwen3.8-flash", "ciphertext");
        when(userLlmConfigMapper.selectById(7L)).thenReturn(config);
        when(userApiKeyCipher.decrypt("ciphertext")).thenReturn("  secret-key  ");

        ResolvedModelConfiguration resolved = provider.resolve(CurrentActor.of(7L));

        assertTrue(resolved.configured());
        assertEquals(ModelCode.QWEN_PLUS, resolved.model());
        assertEquals("secret-key", resolved.apiKey());
        assertFalse(resolved.toString().contains("secret-key"));
    }

    @Test
    void resolve_whenDecryptedKeyBlank_shouldRejectInvalidConfig() {
        UserLlmConfig config = config("deepseek-v4-flash", "ciphertext");
        when(userLlmConfigMapper.selectById(7L)).thenReturn(config);
        when(userApiKeyCipher.decrypt("ciphertext")).thenReturn("  ");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> provider.resolve(CurrentActor.of(7L)));

        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID, exception.getErrorCode());
    }

    private static UserLlmConfig config(String modelCode, String ciphertext) {
        UserLlmConfig config = new UserLlmConfig();
        config.setUserId(7L);
        config.setModelCode(modelCode);
        config.setApiKeyCiphertext(ciphertext);
        return config;
    }
}
