package com.zdmj.aiService.service.impl;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.support.ModelClientFactory;
import com.zdmj.aiService.support.UserModelConfigurationReader.ResolvedConfiguration;
import com.zdmj.aiService.support.UserModelConfigurationReader;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.prompt.PromptLoader;

import reactor.core.publisher.Flux;

/**
 * 每次调用读取用户配置并构建客户端，完成提示词渲染、结构化输出和流式调用。
 */
@Service
public class ModelGatewayImpl implements ModelGateway {

    /**
     * 仅结构化调用附带 JSON Mode。请求 options 与客户端默认项合并。
     * DeepSeek 不支持 json_schema。
     */
    private static final OpenAiChatOptions JSON_OBJECT_OPTIONS = OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
            .build();

    private final PromptLoader promptLoader;
    private final UserModelConfigurationReader configurationReader;
    private final ModelClientFactory clients;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    /**
     * 装配提示词、用户配置查询、供应商客户端和会话记忆。
     *
     * @param promptLoader            提示词加载
     * @param configurationReader     用户模型配置读取与解密
     * @param clients                 供应商客户端参数
     * @param chatMemory              会话记忆，由会话模块提供存储与窗口
     */
    public ModelGatewayImpl(PromptLoader promptLoader, UserModelConfigurationReader configurationReader,
            ModelClientFactory clients, ChatMemory chatMemory) {
        this.promptLoader = promptLoader;
        this.configurationReader = configurationReader;
        this.clients = clients;
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 生成文本。
     *
     * @param actor 当前操作者
     * @param request 模型请求
     * @return 文本
     */
    @Override
    public String generate(CurrentActor actor, ModelRequest request) {
        return prepare(actor, request)
                .user(request.message())
                .call()
                .content();
    }

    /**
     * 生成结构化输出。
     *
     * @param actor 当前操作者
     * @param request 模型请求
     * @param outputType 非空的结构化结果类型
     * @return 结构化输出
     */
    @Override
    public <T> T generateStructured(CurrentActor actor, ModelRequest request, Class<T> outputType) {
        if (outputType == null) {
            throw new IllegalArgumentException("结构化结果类型不能为空");
        }
        return invokeStructured(prepare(actor, request), request.message(), outputType);
    }

    /**
     * 流式生成文本。
     *
     * @param actor 当前操作者
     * @param conversationId 非空的会话主键
     * @param request 模型请求
     * @return 文本流
     */
    @Override
    public Flux<String> stream(CurrentActor actor, Long conversationId, ModelRequest request) {
        if (conversationId == null) {
            throw new IllegalArgumentException("会话主键不能为空");
        }
        return prepare(actor, request)
                .advisors(advisor -> advisor.advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, String.valueOf(conversationId)))
                .user(request.message())
                .stream()
                .content();
    }

    /** 每次读取配置并构建无默认记忆的客户端；未配置时立即终止。 */
    protected ChatClient userChatClient(CurrentActor actor) {
        ResolvedConfiguration resolved = configurationReader.find(actor)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED));
        return ChatClient.builder(clients.buildUserModel(resolved.model(), resolved.apiKey())).build();
    }

    private ChatClientRequestSpec prepare(CurrentActor actor, ModelRequest request) {
        requireActor(actor);
        ChatClientRequestSpec spec = userChatClient(actor).prompt();
        if (StringUtils.hasText(request.promptName())) {
            spec.system(renderPlaceholders(promptLoader.load(request.promptName()), request.promptVars()));
        }
        return spec;
    }

    private <T> T invokeStructured(ChatClientRequestSpec spec, String userMessage, Class<T> outputType) {
        String payload = (userMessage == null ? "" : userMessage)
                + "\n\n请输出 JSON 对象（无 Markdown、无前后说明）。";
        T parsed = spec.options(JSON_OBJECT_OPTIONS)
                .user(payload)
                .call()
                .entity(new JsonOutputConverter<>(outputType));
        if (parsed == null) {
            throw new IllegalStateException("结构化输出解析结果为空");
        }
        return parsed;
    }

    private static void requireActor(CurrentActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
    }

    /**
     * 用变量值替换提示词中的占位符。支持 {@code ${key}} 与 {@code {key}}，只替换 map 中声明的 key。
     */
    static String renderPlaceholders(String template, Map<String, Object> variables) {
        if (!StringUtils.hasText(template) || variables == null || variables.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace("${" + key + "}", value);
            rendered = rendered.replace("{" + key + "}", value);
        }
        return rendered;
    }

    /**
     * 剥 markdown 围栏后再交给 {@link BeanOutputConverter}。
     * 框架 cleaner 能处理多行围栏，但会把单行 {@code ```json {...} ```} 清成空串。
     */
    private static final class JsonOutputConverter<T> implements StructuredOutputConverter<T> {

        private final BeanOutputConverter<T> delegate;

        private JsonOutputConverter(Class<T> outputType) {
            this.delegate = new BeanOutputConverter<>(outputType);
        }

        @Override
        public T convert(String text) {
            return this.delegate.convert(stripCodeFence(text));
        }

        @Override
        public String getFormat() {
            return this.delegate.getFormat();
        }

        private static String stripCodeFence(String text) {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.startsWith("```json")) {
                trimmed = trimmed.substring(7).trim();
            } else if (trimmed.startsWith("```")) {
                trimmed = trimmed.substring(3).trim();
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
            return trimmed;
        }
    }
}
