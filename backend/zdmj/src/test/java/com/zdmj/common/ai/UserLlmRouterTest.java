package com.zdmj.common.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.userAuthService.entity.UserLlmConfig;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLlmRouterTest {

    private static final Long USER_ID = 42L;

    @Mock
    private UserLlmConfigMapper userLlmConfigMapper;

    @Mock
    private ChatMemory chatMemory;

    private UserLlmRouter router;

    @BeforeEach
    void setUp() {
        router = new UserLlmRouter(userLlmConfigMapper, chatMemory);
        ReflectionTestUtils.setField(router, "requireUserConfig", false);
        ReflectionTestUtils.setField(router, "platformFallbackEnabled", true);
        ReflectionTestUtils.setField(router, "requireEncryptionKey", false);
        ReflectionTestUtils.setField(router, "encryptionKey", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(router, "platformBaseUrl",
                "https://dashscope.aliyuncs.com/compatible-mode");
        ReflectionTestUtils.setField(router, "platformApiKey", "platform-sk-test");
        ReflectionTestUtils.setField(router, "platformModel", "qwen3.7-max");
        router.initEncryptor();
    }

    @Test
    void listModelOptions_containsQwenAndDeepSeek() {
        var codes = router.listModelOptions().stream().map(UserLlmRouter.ModelOptionView::code).toList();
        assertTrue(codes.contains("qwen3.6-plus"));
        assertTrue(codes.contains("qwen3.7-max"));
        assertTrue(codes.contains("deepseek-v4-flash"));
        assertTrue(codes.contains("deepseek-v4-pro"));
    }

    @Test
    void encryptDecryptApiKey_roundTrip() {
        String plain = "sk-user-api-key-abcdef12";
        String ciphertext = router.encryptApiKey(plain);
        assertEquals(plain, router.decryptApiKey(ciphertext));
    }

    @Test
    void getChatClient_usesCachedClientUntilEvict() {
        stubUserConfig(ModelEnum.QWEN_PLUS.code(), "sk-qwen-key-12345678");

        ChatClient first = router.getChatClient(USER_ID);
        ChatClient second = router.getChatClient(USER_ID);
        assertSame(first, second);

        router.evict(USER_ID);
        ChatClient afterEvict = router.getChatClient(USER_ID);
        assertNotSame(first, afterEvict);
    }

    @Test
    void switchModelFromQwenToDeepSeek_requiresEvictForNewClient() {
        stubUserConfig(ModelEnum.QWEN_PLUS.code(), "sk-qwen-key-12345678");
        ChatClient qwenClient = router.getChatClient(USER_ID);

        stubUserConfig(ModelEnum.DEEPSEEK_FLASH.code(), "sk-deepseek-key-12345678");
        ChatClient stillCached = router.getChatClient(USER_ID);
        assertSame(qwenClient, stillCached, "未 evict 时应继续复用旧 ChatClient");

        router.evict(USER_ID);
        ChatClient deepSeekClient = router.getChatClient(USER_ID);
        assertNotSame(qwenClient, deepSeekClient);
    }

    @Test
    void getChatClient_fallsBackToPlatformWhenNoUserConfig() {
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(null);

        ChatClient client = router.getChatClient(USER_ID);
        assertTrue(client != null);
    }

    @Test
    void getChatClient_throwsWhenUserConfigRequired() {
        ReflectionTestUtils.setField(router, "requireUserConfig", true);
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> router.getChatClient(USER_ID));
    }

    @Test
    void validateModelCode_rejectsUnknownModel() {
        assertThrows(BusinessException.class, () -> router.validateModelCode("unknown-model"));
    }

    private void stubUserConfig(String modelCode, String apiKey) {
        UserLlmConfig config = new UserLlmConfig();
        config.setUserId(USER_ID);
        config.setModelCode(modelCode);
        config.setApiKeyCiphertext(router.encryptApiKey(apiKey));
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(config);
    }
}
