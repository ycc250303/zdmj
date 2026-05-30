package com.zdmj.common.ai;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.UserApiKeyCipher;
import com.zdmj.userAuthService.entity.UserLlmConfig;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserLlmRouter {
    /**
     * 匹配 URL 尾部的版本号
     */
    private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

    /** 连通性探测：连接超时（毫秒） */
    private static final int CONNECTIVITY_CONNECT_TIMEOUT_MS = 5_000;

    /** 连通性探测：读超时（毫秒） */
    private static final int CONNECTIVITY_READ_TIMEOUT_MS = 30_000;
    
    /**
     * 用户大模型配置Mapper
     */
    private final UserLlmConfigMapper userLlmConfigMapper;
    
    /**
     * 聊天记忆
     */
    private final ChatMemory chatMemory;
    
    /**
     * 缓存ChatClient
     */
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    
    /**
     * 是否需要用户配置
     */
    @Value("${app.ai.user-llm.require-user-config:false}")
    private boolean requireUserConfig;
    
    /**
     * 是否启用平台兜底
     */
    @Value("${app.ai.user-llm.platform-fallback-enabled:true}")
    private boolean platformFallbackEnabled;
    
    /**
     * 是否需要加密密钥
     */
    @Value("${app.ai.user-llm.require-encryption-key:false}")
    private boolean requireEncryptionKey;
    
    /**
     * 加密密钥
     */
    @Value("${app.ai.user-llm.encryption-key:}")
    private String encryptionKey;
    
    /**
     * 平台基础 URL
     */
    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String platformBaseUrl;
    
    /**
     * 平台 API Key
     */
    @Value("${spring.ai.openai.api-key:}")
    private String platformApiKey;
    
    /**
     * 平台模型
     */
    @Value("${spring.ai.openai.chat.options.model:qwen3.7-max}")
    private String platformModel;
    
    /**
     * 文本加密器
     */
    private TextEncryptor textEncryptor;

    @PostConstruct
    void initEncryptor() {
        textEncryptor = UserApiKeyCipher.createEncryptor(encryptionKey, requireEncryptionKey);
    }

    /**
     * 是否启用平台兜底
     * @return
     */
    public boolean isPlatformFallbackEnabled() {
        return platformFallbackEnabled && !requireUserConfig;
    }

    /**
     * 获取 ChatClient
     * @param userId
     * @return
     */
    public ChatClient getChatClient(Long userId) {
        return clientCache.computeIfAbsent(cacheKey(userId, false), k -> createChatClient(userId, false));
    }

    /**
     * 获取带记忆的 ChatClient
     * @param userId
     * @return
     */
    public ChatClient getChatClientWithMemory(Long userId) {
        return clientCache.computeIfAbsent(cacheKey(userId, true), k -> createChatClient(userId, true));
    }

    /**
     * 列出模型选项
     * @return
     */
    public List<ModelOptionView> listModelOptions() {
        return Arrays.stream(ModelEnum.values())
                .map(m -> new ModelOptionView(m.code(), m.displayName()))
                .toList();
    }

    /**
     * 清除缓存
     * @param userId
     */
    public void evict(Long userId) {
        clientCache.remove(cacheKey(userId, false));
        clientCache.remove(cacheKey(userId, true));
        log.info("[UserLlmRouter] evicted cache userId={}", userId);
    }

    /**
     * 验证模型
     * @param modelCode
     */
    public void validateModelCode(String modelCode) {
        ModelEnum.fromCode(modelCode);
    }

    /**
     * 测试连接
     * @param modelCode
     * @param apiKey
     */
    public void testConnection(String modelCode, String apiKey) {
        long startedAt = System.currentTimeMillis();
        try {
            ModelEnum meta = ModelEnum.fromCode(modelCode);
            ChatModel model = buildChatModelForConnectivityTest(meta.baseUrl(), apiKey.trim(), meta.apiModelName());
            ChatClient.builder(model).build().prompt().user("ping").call().content();
            log.info("[UserLlmRouter] testConnection ok modelCode={} apiModel={} elapsedMs={}",
                    modelCode, meta.apiModelName(), System.currentTimeMillis() - startedAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UserLlmRouter] testConnection failed modelCode={} elapsedMs={}",
                    modelCode, System.currentTimeMillis() - startedAt, e);
            throw new BusinessException(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, e);
        }
    }

    /**
     * 创建 ChatClient
     * @param userId
     * @param withMemory
     * @return
     */
    private ChatClient createChatClient(Long userId, boolean withMemory) {
        ResolvedProvider resolved = resolveProvider(userId);
        ChatModel chatModel = buildChatModel(resolved.baseUrl(), resolved.apiKey(), resolved.model(), null);
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (withMemory) {
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        log.info("[UserLlmRouter] created ChatClient userId={} memory={} platformDefault={} model={}",
                userId, withMemory, resolved.platformDefault(), resolved.model());
        return builder.build();
    }

    /**
     * 解析提供者
     * @param userId
     * @return
     */
    private ResolvedProvider resolveProvider(Long userId) {
        UserLlmConfig config = userLlmConfigMapper.selectById(userId);
        if (config != null) {
            ModelEnum meta = ModelEnum.fromCode(config.getModelCode());
            String apiKey = decryptApiKey(config.getApiKeyCiphertext());
            if (!StringUtils.hasText(apiKey)) {
                throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
            }
            return new ResolvedProvider(meta.baseUrl(), apiKey.trim(), meta.apiModelName(), false);
        }
        if (requireUserConfig || !platformFallbackEnabled) {
            throw new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED);
        }
        if (!StringUtils.hasText(platformApiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED);
        }
        return new ResolvedProvider(platformBaseUrl, platformApiKey.trim(), platformModel, true);
    }

    /**
     * 构建 ChatModel
     * @param baseUrl
     * @param apiKey
     * @param modelName
     * @return
     */
    private ChatModel buildChatModel(String baseUrl, String apiKey, String modelName,
            OpenAiChatOptions overrideOptions) {
        OpenAiApi api = buildOpenAiApi(baseUrl, apiKey, 10_000, 300_000);
        OpenAiChatOptions options = overrideOptions != null ? overrideOptions
                : OpenAiChatOptions.builder().model(modelName).build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildChatModelForConnectivityTest(String baseUrl, String apiKey, String modelName) {
        OpenAiApi api = buildOpenAiApi(baseUrl, apiKey, CONNECTIVITY_CONNECT_TIMEOUT_MS, CONNECTIVITY_READ_TIMEOUT_MS);
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(connectivityTestOptions(modelName))
                .build();
    }

    /**
     * 连通性探测：最少生成、关闭思考模式，避免 qwen3 / deepseek-v4 走完整推理链。
     */
    private OpenAiChatOptions connectivityTestOptions(String apiModelName) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(apiModelName)
                .maxTokens(1)
                .temperature(0.0);
        Map<String, Object> extraBody = new HashMap<>();
        if (StringUtils.hasText(apiModelName)) {
            if (apiModelName.startsWith("deepseek-v4")) {
                extraBody.put("thinking", Map.of("type", "disabled"));
            }
            if (apiModelName.startsWith("qwen3")) {
                extraBody.put("enable_thinking", false);
            }
        }
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        return builder.build();
    }

    /**
     * 构建 OpenAiApi
     * @param baseUrl
     * @param apiKey
     * @return
     */
    private OpenAiApi buildOpenAiApi(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder);
        if (baseUrlContainsVersion(baseUrl)) {
            apiBuilder.completionsPath("/chat/completions").embeddingsPath("/embeddings");
        }
        return apiBuilder.build();
    }

    /**
     * 判断 URL 是否包含版本号
     * @param baseUrl
     * @return
     */
    private boolean baseUrlContainsVersion(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        return TRAILING_VERSION.matcher(stripTrailingSlashes(baseUrl.trim())).find();
    }

    /**
     * 去除尾部斜杠
     * @param value
     * @return
     */
    private String stripTrailingSlashes(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 加密 API Key
     * @param plainApiKey
     * @return
     */
    public String encryptApiKey(String plainApiKey) {
        return UserApiKeyCipher.encrypt(textEncryptor, plainApiKey);
    }

    /**
     * 解密 API Key
     * @param ciphertext
     * @return
     */
    public String decryptApiKey(String ciphertext) {
        return UserApiKeyCipher.decrypt(textEncryptor, ciphertext);
    }

    /**
     * 缓存 Key
     * @param userId
     * @param memory
     * @return
     */
    private static String cacheKey(Long userId, boolean memory) {
        return userId + (memory ? ":memory" : ":plain");
    }

    /**
     * 模型选项视图
     * @param code
     * @param displayName
     * @return
     */
    public record ModelOptionView(String code, String displayName) {
    }

    /**
     * 解析后的提供者
     * @param baseUrl
     * @param apiKey
     * @param model
     * @param platformDefault
     * @return
     */
    private record ResolvedProvider(String baseUrl, String apiKey, String model, boolean platformDefault) {
    }
}