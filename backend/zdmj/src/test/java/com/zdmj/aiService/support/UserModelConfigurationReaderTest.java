package com.zdmj.aiService.support;

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

import com.zdmj.aiService.entity.UserLlmConfig;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class UserModelConfigurationReaderTest {

    @Mock
    private UserLlmConfigMapper userLlmConfigMapper;
    @Mock
    private UserApiKeyCipher userApiKeyCipher;

    private UserModelConfigurationReader reader;

    @BeforeEach
    void setUp() {
        reader = new UserModelConfigurationReader(userLlmConfigMapper, userApiKeyCipher);
    }

    @Test
    void find_whenActorMissing_shouldRejectWithoutReadingStorage() {
        assertEquals(ErrorCode.USER_NOT_LOGIN,
                assertThrows(BusinessException.class, () -> reader.find(null)).getErrorCode());
        assertEquals(ErrorCode.USER_NOT_LOGIN,
                assertThrows(BusinessException.class, () -> reader.find(CurrentActor.of(null))).getErrorCode());

        verifyNoInteractions(userLlmConfigMapper, userApiKeyCipher);
    }

    @Test
    void find_whenConfigMissing_shouldReturnEmpty() {
        when(userLlmConfigMapper.selectById(7L)).thenReturn(null);

        assertTrue(reader.find(CurrentActor.of(7L)).isEmpty());
        verify(userApiKeyCipher, never()).decrypt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void find_whenConfigExists_shouldParseModelAndTrimDecryptedKey() {
        UserLlmConfig config = config("qwen3.8-flash", "ciphertext");
        when(userLlmConfigMapper.selectById(7L)).thenReturn(config);
        when(userApiKeyCipher.decrypt("ciphertext")).thenReturn("  secret-key  ");

        ResolvedModelConfiguration resolved = reader.find(CurrentActor.of(7L)).orElseThrow();
        assertEquals(ModelCode.QWEN_PLUS, resolved.model());
        assertEquals("secret-key", resolved.apiKey());
        assertFalse(resolved.toString().contains("secret-key"));
    }

    @Test
    void find_whenDecryptedKeyBlank_shouldRejectInvalidConfig() {
        UserLlmConfig config = config("deepseek-v4-flash", "ciphertext");
        when(userLlmConfigMapper.selectById(7L)).thenReturn(config);
        when(userApiKeyCipher.decrypt("ciphertext")).thenReturn("  ");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reader.find(CurrentActor.of(7L)));

        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID, exception.getErrorCode());
    }

    @Test
    void find_whenStorageFails_shouldPropagateFailure() {
        IllegalStateException storageFailure = new IllegalStateException("storage unavailable");
        when(userLlmConfigMapper.selectById(7L)).thenThrow(storageFailure);
        org.junit.jupiter.api.Assertions.assertSame(storageFailure,
                assertThrows(IllegalStateException.class, () -> reader.find(CurrentActor.of(7L))));
        verifyNoInteractions(userApiKeyCipher);
    }

    @Test
    void resolvedConfiguration_shouldRejectIncompleteValuesAndHideKey() {
        assertThrows(NullPointerException.class, () -> new ResolvedModelConfiguration(null, "secret"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedModelConfiguration(ModelCode.DEEPSEEK_FLASH, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedModelConfiguration(ModelCode.DEEPSEEK_FLASH, "  "));
        ResolvedModelConfiguration config = new ResolvedModelConfiguration(ModelCode.DEEPSEEK_FLASH, "  secret  ");
        assertEquals("secret", config.apiKey());
        assertFalse(config.toString().contains("secret"));
    }

    private static UserLlmConfig config(String modelCode, String ciphertext) {
        UserLlmConfig config = new UserLlmConfig();
        config.setUserId(7L);
        config.setModelCode(modelCode);
        config.setApiKeyCiphertext(ciphertext);
        return config;
    }
}
