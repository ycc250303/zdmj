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

/**
 * 按 userId 路由 Chat 模型：解析用户/平台配置，构建并缓存 {@link ChatClient}。
 * <p>
 * 业务层通过 {@link ChatUtil} 调用 {@link #getChatClient(Long)} 或
 * {@link #getChatClientWithMemory(Long)}，不直接依赖全局 ChatModel Bean。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLlmRouter {

    /**
     * 匹配 URL 尾部的版本号（如 /v1），用于决定是否显式设置 completionsPath。
     */
    private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

    /** 业务对话：连接超时（毫秒） */
    private static final int CHAT_CONNECT_TIMEOUT_MS = 10_000;

    /** 业务对话：读超时（毫秒） */
    private static final int CHAT_READ_TIMEOUT_MS = 300_000;

    /** 连通性探测：连接超时（毫秒） */
    private static final int PROBE_CONNECT_TIMEOUT_MS = 5_000;

    /** 连通性探测：读超时（毫秒） */
    private static final int PROBE_READ_TIMEOUT_MS = 30_000;

    /**
     * 用户大模型配置 Mapper
     */
    private final UserLlmConfigMapper userLlmConfigMapper;

    /**
     * 聊天记忆（多轮对话 Advisor 使用）
     */
    private final ChatMemory chatMemory;

    /**
     * 按 userId 缓存 ChatClient（plain / memory 各一份）
     */
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 是否强制用户自配 LLM（true 时无配置直接报错，不走平台兜底）
     */
    @Value("${app.ai.user-llm.require-user-config:false}")
    private boolean requireUserConfig;

    /**
     * 无用户配置时是否允许使用平台 Key 兜底
     */
    @Value("${app.ai.user-llm.platform-fallback-enabled:true}")
    private boolean platformFallbackEnabled;

    /**
     * 是否强制配置 API Key 加密密钥
     */
    @Value("${app.ai.user-llm.require-encryption-key:false}")
    private boolean requireEncryptionKey;

    /**
     * 用户 API Key 落库加密密钥
     */
    @Value("${app.ai.user-llm.encryption-key:}")
    private String encryptionKey;

    /**
     * 平台兜底：OpenAI 兼容 baseUrl
     */
    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String platformBaseUrl;

    /**
     * 平台兜底：API Key
     */
    @Value("${spring.ai.openai.api-key:}")
    private String platformApiKey;

    /**
     * 平台兜底：默认 Chat 模型名
     */
    @Value("${spring.ai.openai.chat.options.model:qwen3.7-max}")
    private String platformModel;

    /**
     * DeepSeek 平台 API Key（简历识别等固定 deepseek 场景优先使用）
     */
    @Value("${app.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    /**
     * 按平台指定模型缓存的 ChatClient（与用户配置无关）
     */
    private final Map<String, ChatClient> platformClientCache = new ConcurrentHashMap<>();

    /**
     * 用户 API Key 加解密器
     */
    private TextEncryptor textEncryptor;

    @PostConstruct
    void initEncryptor() {
        textEncryptor = UserApiKeyCipher.createEncryptor(encryptionKey, requireEncryptionKey);
    }

    /**
     * 当前环境是否允许「未配置用户使用平台默认模型」
     *
     * @return 平台兜底且非强制自配时为 true
     */
    public boolean isPlatformFallbackEnabled() {
        return platformFallbackEnabled && !requireUserConfig;
    }

    /**
     * 获取无会话记忆的 ChatClient（画像、报告、结构化单次调用等）
     *
     * @param userId 当前登录用户 ID
     * @return 按用户路由后的 ChatClient
     */
    public ChatClient getChatClient(Long userId) {
        return cachedClient(userId, false);
    }

    /**
     * 获取带 JDBC 会话记忆的 ChatClient（多轮对话）
     *
     * @param userId 当前登录用户 ID
     * @return 带 MessageChatMemoryAdvisor 的 ChatClient
     */
    public ChatClient getChatClientWithMemory(Long userId) {
        return cachedClient(userId, true);
    }

    /**
     * 获取平台指定模型的 ChatClient（忽略用户自配 LLM，用于简历识别等固定模型任务）
     *
     * @param model 平台模型目录项
     * @return 无会话记忆的 ChatClient
     */
    public ChatClient getPlatformChatClient(ModelEnum model) {
        return platformClientCache.computeIfAbsent("platform:" + model.code(),
                k -> createPlatformChatClient(model));
    }

    /**
     * 列出可选模型（来自 {@link ModelEnum} 静态目录）
     *
     * @return code + displayName 列表
     */
    public List<ModelOptionView> listModelOptions() {
        return Arrays.stream(ModelEnum.values())
                .map(m -> new ModelOptionView(m.code(), m.displayName()))
                .toList();
    }

    /**
     * 清除指定用户的 ChatClient 缓存（保存/删除 LLM 配置后须调用）
     *
     * @param userId 用户 ID
     */
    public void evict(Long userId) {
        clientCache.remove(cacheKey(userId, false));
        clientCache.remove(cacheKey(userId, true));
        log.info("[UserLlmRouter] evicted cache userId={}", userId);
    }

    /**
     * 校验 modelCode 是否在 {@link ModelEnum} 目录内
     *
     * @param modelCode 前端传入的模型 code
     */
    public void validateModelCode(String modelCode) {
        ModelEnum.fromCode(modelCode);
    }

    /**
     * 连通性测试：使用请求体中的 modelCode + apiKey，不读库、不走 clientCache。
     * 使用短超时与 maxTokens=1 的轻量探测，仅验证 Key 与模型是否可用。
     *
     * @param modelCode 模型 code
     * @param apiKey    明文 API Key
     */
    public void testConnection(String modelCode, String apiKey) {
        long startedAt = System.currentTimeMillis();
        try {
            ModelEnum meta = ModelEnum.fromCode(modelCode);
            ChatClient.builder(buildChatModel(meta.baseUrl(), apiKey.trim(), meta.apiModelName(),
                    PROBE_CONNECT_TIMEOUT_MS, PROBE_READ_TIMEOUT_MS, true))
                    .build().prompt().user("ping").call().content();
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
     * 加密用户 API Key（落库前调用）
     *
     * @param plainApiKey 明文 Key
     * @return 密文
     */
    public String encryptApiKey(String plainApiKey) {
        return UserApiKeyCipher.encrypt(textEncryptor, plainApiKey);
    }

    /**
     * 解密用户 API Key（路由解析时调用）
     *
     * @param ciphertext 密文
     * @return 明文 Key
     */
    public String decryptApiKey(String ciphertext) {
        return UserApiKeyCipher.decrypt(textEncryptor, ciphertext);
    }

    /**
     * 从缓存获取或懒创建 ChatClient
     *
     * @param userId      用户 ID
     * @param withMemory  是否挂载会话记忆 Advisor
     * @return ChatClient 实例
     */
    private ChatClient cachedClient(Long userId, boolean withMemory) {
        return clientCache.computeIfAbsent(cacheKey(userId, withMemory),
                k -> createChatClient(userId, withMemory));
    }

    /**
     * 解析路由并构建 ChatClient
     *
     * @param userId     用户 ID
     * @param withMemory 是否带记忆
     * @return 新建并返回 ChatClient（随后写入缓存）
     */
    private ChatClient createChatClient(Long userId, boolean withMemory) {
        ResolvedProvider resolved = resolveProvider(userId);
        ChatClient.Builder builder = ChatClient.builder(
                chatModel(resolved.baseUrl(), resolved.apiKey(), resolved.model()));
        if (withMemory) {
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        log.info("[UserLlmRouter] created ChatClient userId={} memory={} platformDefault={} model={}",
                userId, withMemory, resolved.platformDefault(), resolved.model());
        return builder.build();
    }

    /**
     * 路由决策：优先用户自配，否则平台兜底。
     * <p>
     * 用户配置时通过 {@link ModelEnum} 将 modelCode 映射为 baseUrl + apiModelName。
     *
     * @param userId 用户 ID
     * @return 解析后的连接四元组
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
     * 解析平台指定模型连接参数（不读取用户 LLM 配置）
     */
    private ResolvedProvider resolvePlatformProvider(ModelEnum model) {
        String apiKey = resolvePlatformApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED.getCode(),
                    "平台大模型 API Key 未配置，请配置 DEEPSEEK_API_KEY 或 SPRING_AI_OPENAI_API_KEY");
        }
        return new ResolvedProvider(model.baseUrl(), apiKey.trim(), model.apiModelName(), true);
    }

    private String resolvePlatformApiKey(ModelEnum model) {
        if (model == ModelEnum.DEEPSEEK_FLASH || model == ModelEnum.DEEPSEEK_PRO) {
            if (StringUtils.hasText(deepseekApiKey)) {
                return deepseekApiKey;
            }
        }
        return platformApiKey;
    }

    private ChatClient createPlatformChatClient(ModelEnum model) {
        ResolvedProvider resolved = resolvePlatformProvider(model);
        ChatClient client = ChatClient.builder(
                chatModel(resolved.baseUrl(), resolved.apiKey(), resolved.model())).build();
        log.info("[UserLlmRouter] created platform ChatClient model={} baseUrl={}",
                resolved.model(), resolved.baseUrl());
        return client;
    }

    /**
     * 构建业务用 ChatModel（长超时、正常生成参数）
     *
     * @param baseUrl   厂商 baseUrl
     * @param apiKey    API Key
     * @param modelName 真实 API 模型名
     * @return OpenAiChatModel 实例
     */
    private ChatModel chatModel(String baseUrl, String apiKey, String modelName) {
        return buildChatModel(baseUrl, apiKey, modelName,
                CHAT_CONNECT_TIMEOUT_MS, CHAT_READ_TIMEOUT_MS, false);
    }

    /**
     * 构建 ChatModel：OpenAiApi（HTTP） + 默认 ChatOptions。
     *
     * @param baseUrl            厂商 baseUrl
     * @param apiKey             API Key
     * @param modelName          真实 API 模型名
     * @param connectTimeoutMs   连接超时
     * @param readTimeoutMs      读超时
     * @param connectivityProbe  true 时为连通性轻量探测（短超时、maxTokens=1）
     * @return OpenAiChatModel 实例
     */
    private ChatModel buildChatModel(String baseUrl, String apiKey, String modelName,
            int connectTimeoutMs, int readTimeoutMs, boolean connectivityProbe) {
        return OpenAiChatModel.builder()
                .openAiApi(buildOpenAiApi(baseUrl, apiKey, connectTimeoutMs, readTimeoutMs))
                .defaultOptions(chatOptions(modelName, connectivityProbe))
                .build();
    }

    /**
     * 组装 OpenAiChatOptions：业务与探测共用，探测时额外限制 maxTokens。
     *
     * @param modelName          真实 API 模型名
     * @param connectivityProbe  是否为连通性探测
     * @return Chat 请求默认选项
     */
    private OpenAiChatOptions chatOptions(String modelName, boolean connectivityProbe) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(modelName);
        if (connectivityProbe) {
            builder.maxTokens(1).temperature(0.0);
        }
        Map<String, Object> extraBody = thinkingDisabledExtraBody(modelName);
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        return builder.build();
    }

    /**
     * qwen3 / deepseek-v4 非流式默认关思考链，避免探测与对话首 token 过慢。
     *
     * @param apiModelName 真实 API 模型名
     * @return 写入 extra_body 的参数（可能为空 Map）
     */
    private static Map<String, Object> thinkingDisabledExtraBody(String apiModelName) {
        Map<String, Object> extraBody = new HashMap<>();
        if (!StringUtils.hasText(apiModelName)) {
            return extraBody;
        }
        if (apiModelName.startsWith("deepseek-v4")) {
            extraBody.put("thinking", Map.of("type", "disabled"));
        }
        if (apiModelName.startsWith("qwen3")) {
            extraBody.put("enable_thinking", false);
        }
        return extraBody;
    }

    /**
     * 构建 OpenAiApi HTTP 客户端（Spring AI 传输层）。
     * DashScope baseUrl 不含 /v1 时不改 path；DeepSeek 等含版本号时需显式指定 completionsPath。
     *
     * @param baseUrl          厂商 baseUrl
     * @param apiKey           API Key
     * @param connectTimeoutMs 连接超时
     * @param readTimeoutMs    读超时
     * @return OpenAiApi 实例
     */
    private OpenAiApi buildOpenAiApi(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory));
        if (baseUrlContainsVersion(baseUrl)) {
            apiBuilder.completionsPath("/chat/completions").embeddingsPath("/embeddings");
        }
        return apiBuilder.build();
    }

    /**
     * 判断 baseUrl 是否已包含 /v1 等版本后缀
     *
     * @param baseUrl 厂商 baseUrl
     * @return 含版本号时为 true
     */
    private static boolean baseUrlContainsVersion(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return TRAILING_VERSION.matcher(normalized).find();
    }

    /**
     * 生成 clientCache 键：同一用户 plain / memory 各缓存一份
     *
     * @param userId  用户 ID
     * @param memory  是否带记忆
     * @return 缓存键
     */
    private static String cacheKey(Long userId, boolean memory) {
        return userId + (memory ? ":memory" : ":plain");
    }

    /**
     * 模型选项视图（供配置 API 列表展示）
     *
     * @param code        模型 code
     * @param displayName 展示名
     */
    public record ModelOptionView(String code, String displayName) {
    }

    /**
     * 路由解析结果：连接 LLM 所需的 baseUrl、apiKey、model，及是否平台兜底。
     *
     * @param baseUrl         厂商 baseUrl
     * @param apiKey          明文 API Key
     * @param model           真实 API 模型名
     * @param platformDefault true 表示走平台 Key，false 表示用户自配
     */
    private record ResolvedProvider(String baseUrl, String apiKey, String model, boolean platformDefault) {
    }
}
