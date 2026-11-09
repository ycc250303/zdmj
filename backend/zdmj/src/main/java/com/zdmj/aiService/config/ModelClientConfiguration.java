package com.zdmj.aiService.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.zdmj.aiService.enums.ModelCode;

/**
 * 供应商客户端参数：超时和 OpenAI 兼容接口组装。对话调用使用用户保存的模型与密钥。
 */
@Component
public class ModelClientConfiguration {

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
        return buildChatModel(model, apiKey, false);
    }

    /**
     * 用短超时和单 token 探测密钥是否可用。
     *
     * @param model 已解析的模型目录项
     * @param apiKey    明文密钥
     * @return 探测用对话模型
     */
    public ChatModel buildProbe(ModelCode model, String apiKey) {
        return buildChatModel(model, apiKey, true);
    }

    private ChatModel buildChatModel(ModelCode model, String apiKey, boolean connectivityProbe) {
        int connectTimeout = connectivityProbe ? PROBE_CONNECT_TIMEOUT_MS : CHAT_CONNECT_TIMEOUT_MS;
        int readTimeout = connectivityProbe ? PROBE_READ_TIMEOUT_MS : CHAT_READ_TIMEOUT_MS;
        return OpenAiChatModel.builder()
                .openAiApi(buildOpenAiApi(model.baseUrl(), apiKey, connectTimeout, readTimeout))
                .defaultOptions(chatOptions(model, connectivityProbe))
                .build();
    }

    private OpenAiChatOptions chatOptions(ModelCode model, boolean connectivityProbe) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model.apiModelName()).extraBody(model.thinkingDisabledExtraBody());
        if (connectivityProbe) {
            builder.maxTokens(1).temperature(0.0);
        }
        return builder.build();
    }

    private OpenAiApi buildOpenAiApi(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath("/v1/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }
}
