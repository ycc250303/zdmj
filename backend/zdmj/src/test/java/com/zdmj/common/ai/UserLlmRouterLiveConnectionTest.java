package com.zdmj.common.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
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
 * 真实连通性测试：仅在本地显式开启且具备 API Key 时运行；CI 始终跳过。
 *
 * <p>即便仓库/本机已配置 {@code DASHSCOPE_API_KEY}、{@code DEEPSEEK_API_KEY}（GitHub Actions
 * 常见），在 {@code CI=true} 或 {@code GITHUB_ACTIONS=true} 下也不会执行，避免占位密钥或网络限制导致流水线失败。</p>
 *
 * <p>本地示例：
 * {@code ZDMJ_RUN_LIVE_LLM_TESTS=true DASHSCOPE_API_KEY=sk-xxx mvn test -Dtest=UserLlmRouterLiveConnectionTest}
 * </p>
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
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
    @EnabledIfEnvironmentVariable(named = "ZDMJ_RUN_LIVE_LLM_TESTS", matches = "(?i)true|yes|1")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    void testConnection_qwen36Plus() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.QWEN_PLUS.code(),
                System.getenv("DASHSCOPE_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ZDMJ_RUN_LIVE_LLM_TESTS", matches = "(?i)true|yes|1")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    void testConnection_qwen37Max() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.QWEN_MAX.code(),
                System.getenv("DASHSCOPE_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ZDMJ_RUN_LIVE_LLM_TESTS", matches = "(?i)true|yes|1")
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void testConnection_deepSeekV4Pro() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.DEEPSEEK_PRO.code(),
                System.getenv("DEEPSEEK_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ZDMJ_RUN_LIVE_LLM_TESTS", matches = "(?i)true|yes|1")
    @EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
    void testConnection_deepSeekV4Flash() {
        assertDoesNotThrow(() -> router.testConnection(
                ModelEnum.DEEPSEEK_FLASH.code(),
                System.getenv("DEEPSEEK_API_KEY")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ZDMJ_RUN_LIVE_LLM_TESTS", matches = "(?i)true|yes|1")
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
