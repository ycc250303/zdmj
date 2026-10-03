package com.zdmj.aiService.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.zdmj.aiService.model.ModelCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 供应商客户端参数：超时、平台密钥和 OpenAI 兼容接口组装。
 */
@Component
public class ModelClientConfiguration {

    private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

    /** 业务对话：连接超时（毫秒） */
    private static final int CHAT_CONNECT_TIMEOUT_MS = 10_000;

    /** 业务对话：读超时（毫秒） */
    private static final int CHAT_READ_TIMEOUT_MS = 300_000;

    /** 连通性探测：连接超时（毫秒） */
    private static final int PROBE_CONNECT_TIMEOUT_MS = 5_000;

    /** 连通性探测：读超时（毫秒） */
    private static final int PROBE_READ_TIMEOUT_MS = 30_000;

    private final boolean platformFallbackEnabled;
    private final String platformBaseUrl;
    private final String platformApiKey;
    private final String platformModel;
    private final String deepseekApiKey;

    /**
     * 读取平台兜底与 DeepSeek 密钥。
     *
     * @param platformFallbackEnabled 无用户配置时是否允许平台密钥
     * @param platformBaseUrl         平台 base URL
     * @param platformApiKey          平台 API Key
     * @param platformModel           平台默认模型名
     * @param deepseekApiKey          DeepSeek 平台密钥
     */
    public ModelClientConfiguration(
            @Value("${app.ai.user-llm.platform-fallback-enabled:true}") boolean platformFallbackEnabled,
            @Value("${spring.ai.openai.base-url}") String platformBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String platformApiKey,
            @Value("${spring.ai.openai.chat.options.model}") String platformModel,
            @Value("${app.ai.deepseek.api-key:}") String deepseekApiKey) {
        this.platformFallbackEnabled = platformFallbackEnabled;
        this.platformBaseUrl = platformBaseUrl;
        this.platformApiKey = platformApiKey;
        this.platformModel = platformModel;
        this.deepseekApiKey = deepseekApiKey;
    }

    public boolean platformFallbackEnabled() {
        return platformFallbackEnabled;
    }

    public String platformModelName() {
        return platformModel;
    }

    /**
     * 简历识别模型：已配置 DeepSeek 密钥时用 Flash；否则在平台密钥可用时用通义 Flash。
     *
     * @return 平台模型
     * @throws BusinessException 两套平台密钥都为空时抛出 {@link ErrorCode#USER_LLM_NOT_CONFIGURED}
     */
    public ModelCode resumeImportModel() {
        if (StringUtils.hasText(deepseekApiKey)) {
            return ModelCode.DEEPSEEK_FLASH;
        }
        if (!StringUtils.hasText(platformApiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED,
                    "平台大模型 API Key 未配置，请配置 DEEPSEEK_API_KEY 或 SPRING_AI_OPENAI_API_KEY");
        }
        return ModelCode.QWEN_PLUS;
    }

    /**
     * 按用户目录项构建业务客户端。
     *
     * @param model  目录项
     * @param apiKey 明文密钥
     * @return 对话模型
     */
    public ChatModel buildUserModel(ModelCode model, String apiKey) {
        return buildChatModel(model.baseUrl(), apiKey, model.apiModelName(), false);
    }

    /**
     * 构建平台默认模型。关闭兜底或平台密钥为空时终止。
     *
     * @return 对话模型
     * @throws BusinessException 不允许兜底或平台密钥为空时抛出 {@link ErrorCode#USER_LLM_NOT_CONFIGURED}
     */
    public ChatModel buildPlatformDefaultModel() {
        if (!platformFallbackEnabled || !StringUtils.hasText(platformApiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED);
        }
        return buildChatModel(platformBaseUrl, platformApiKey.trim(), platformModel, false);
    }

    /**
     * 按目录项构建平台模型，DeepSeek 使用独立密钥。
     *
     * @param model 平台模型
     * @return 对话模型
     * @throws BusinessException 对应密钥为空时抛出 {@link ErrorCode#USER_LLM_NOT_CONFIGURED}
     */
    public ChatModel buildPlatformModel(ModelCode model) {
        String apiKey = platformApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED,
                    "平台大模型 API Key 未配置，请配置 DEEPSEEK_API_KEY 或 SPRING_AI_OPENAI_API_KEY");
        }
        return buildChatModel(model.baseUrl(), apiKey.trim(), model.apiModelName(), false);
    }

    /**
     * 用短超时和单 token 探测密钥是否可用。
     *
     * @param modelCode 模型码
     * @param apiKey    明文密钥
     * @return 探测用对话模型
     * @throws BusinessException 模型码非法时抛出 {@link ErrorCode#USER_LLM_CONFIG_INVALID}
     */
    public ChatModel buildProbe(String modelCode, String apiKey) {
        ModelCode model = ModelCode.fromCode(modelCode);
        return buildChatModel(model.baseUrl(), apiKey.trim(), model.apiModelName(), true);
    }

    private String platformApiKey(ModelCode model) {
        if (model == ModelCode.DEEPSEEK_FLASH || model == ModelCode.DEEPSEEK_PRO) {
            return StringUtils.hasText(deepseekApiKey) ? deepseekApiKey : null;
        }
        return platformApiKey;
    }

    private ChatModel buildChatModel(String baseUrl, String apiKey, String modelName, boolean connectivityProbe) {
        int connectTimeout = connectivityProbe ? PROBE_CONNECT_TIMEOUT_MS : CHAT_CONNECT_TIMEOUT_MS;
        int readTimeout = connectivityProbe ? PROBE_READ_TIMEOUT_MS : CHAT_READ_TIMEOUT_MS;
        return OpenAiChatModel.builder()
                .openAiApi(buildOpenAiApi(baseUrl, apiKey, connectTimeout, readTimeout))
                .defaultOptions(chatOptions(modelName, connectivityProbe))
                .build();
    }

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
     * qwen3 / deepseek-v4 非流式默认关闭思考链，缩短探测和对话首 token。
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
}
