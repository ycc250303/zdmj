package com.zdmj.aiService.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 供应商客户端参数：超时和 OpenAI 兼容接口组装。对话调用使用用户保存的模型与密钥。
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
     * qwen3 / deepseek 非流式默认关闭思考链，缩短探测和对话首 token。
     */
    private static Map<String, Object> thinkingDisabledExtraBody(String apiModelName) {
        Map<String, Object> extraBody = new HashMap<>();
        if (!StringUtils.hasText(apiModelName)) {
            return extraBody;
        }
        if (apiModelName.startsWith("deepseek")) {
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
