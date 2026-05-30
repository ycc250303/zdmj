package com.zdmj.common.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;

import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实连通性测试：仅在本地设置环境变量时运行，CI 默认跳过。
 *
 * <p>示例：
 * {@code DASHSCOPE_API_KEY=sk-xxx DEEPSEEK_API_KEY=sk-yyy mvn test -Dtest=UserLlmRouterLiveConnectionTest}
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserLlmRouterLiveConnectionTest {

    @Mock
    private UserLlmConfigMapper userLlmConfigMapper;

    @Mock
    private ChatMemory chatMemory;

    private UserLlmRouter router;

    @BeforeEach
    void setUp() {
        router = new UserLlmRouter(userLlmConfigMapper, chatMemory);
        ReflectionTestUtils.setField(router, "requireUserConfig", false);
        ReflectionTestUtils.setField(router, "platformFallbackEnabled", false);
        ReflectionTestUtils.setField(router, "requireEncryptionKey", false);
        ReflectionTestUtils.setField(router, "encryptionKey", "0123456789abcdef0123456789abcdef");
        router.initEncryptor();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    void testConnection_qwen36Plus() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.QWEN_PLUS.code(),
                System.getenv("DASHSCOPE_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    void testConnection_qwen37Max() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.QWEN_MAX.code(),
                System.getenv("DASHSCOPE_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void testConnection_deepSeekV4Pro() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.DEEPSEEK_PRO.code(),
                System.getenv("DEEPSEEK_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void testConnection_deepSeekV4Flash() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.DEEPSEEK_FLASH.code(),
                System.getenv("DEEPSEEK_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void switchModels_bothProvidersReachable() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.QWEN_PLUS.code(),
                System.getenv("DASHSCOPE_API_KEY")));
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.DEEPSEEK_FLASH.code(),
                System.getenv("DEEPSEEK_API_KEY")));
        assertTrue(router.listModelOptions().size() >= 4);
    }
}
