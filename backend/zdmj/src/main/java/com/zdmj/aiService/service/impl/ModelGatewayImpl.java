package com.zdmj.aiService.service.impl;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.support.ResolvedModelConfiguration;
import com.zdmj.aiService.support.UserModelConfigurationReader;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.prompt.PromptLoader;

import lombok.extern.slf4j.Slf4j;

import reactor.core.publisher.Flux;

/**
 * 按用户配置构建客户端，完成提示词渲染、结构化输出和流式调用。
 * 客户端缓存由本类持有，配置管理通过模块内部方法清除缓存。
 */
@Slf4j
@Service
public class ModelGatewayImpl implements ModelGateway {

    /**
     * 仅结构化调用附带 JSON Mode。请求 options 与客户端默认项合并，不改缓存的 ChatClient。
     * DeepSeek 不支持 json_schema。
     */
    private static final OpenAiChatOptions JSON_OBJECT_OPTIONS = OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
            .build();

    private final PromptLoader promptLoader;
    private final UserModelConfigurationReader configurationReader;
    private final ModelClientConfiguration clients;
    private final ChatMemory chatMemory;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 装配提示词、用户配置查询、供应商客户端和会话记忆。
     *
     * @param promptLoader            提示词加载
     * @param configurationReader     用户模型配置读取与解密
     * @param clients                 供应商客户端参数
     * @param chatMemory              会话记忆，由会话模块提供存储与窗口
     */
    public ModelGatewayImpl(PromptLoader promptLoader, UserModelConfigurationReader configurationReader,
            ModelClientConfiguration clients, ChatMemory chatMemory) {
        this.promptLoader = promptLoader;
        this.configurationReader = configurationReader;
        this.clients = clients;
        this.chatMemory = chatMemory;
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
        requireUserId(actor);
        return applySystemPrompt(userChatClient(actor, false).prompt(), request.promptName(), request.promptVars())
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
        requireUserId(actor);
        if (outputType == null) {
            throw new IllegalArgumentException("结构化结果类型不能为空");
        }
        ChatClientRequestSpec spec = userChatClient(actor, false).prompt();
        return invokeStructured(
                applySystemPrompt(spec, request.promptName(), request.promptVars()),
                request.message(),
                outputType);
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
        requireUserId(actor);
        return applySystemPrompt(
                userChatClient(actor, true).prompt()
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID,
                                String.valueOf(conversationId))),
                request.promptName(),
                request.promptVars())
                .user(request.message())
                .stream()
                .content();
    }

    /**
     * 清除当前用户的普通客户端与会话客户端缓存。
     *
     * @param actor 当前操作者
     * @throws BusinessException 身份缺失时返回未登录错误
     */
    void evict(CurrentActor actor) {
        Long userId = requireUserId(actor);
        clientCache.remove(cacheKey(userId, false));
        clientCache.remove(cacheKey(userId, true));
        log.info("[ModelGateway] evicted cache userId={}", userId);
    }

    /**
     * 取得无记忆或带记忆的用户客户端。测试替身可替换本方法，避免访问外部模型。
     *
     * @param actor      当前操作者
     * @param withMemory 是否挂载会话记忆
     * @return 客户端
     */
    protected ChatClient userChatClient(CurrentActor actor, boolean withMemory) {
        Long userId = requireUserId(actor);
        return clientCache.computeIfAbsent(cacheKey(userId, withMemory), key -> createUserClient(userId, withMemory));
    }

    /**
     * 按用户已保存的模型与密钥构建客户端。未配置时立即终止。
     *
     * @param userId     用户主键
     * @param withMemory 是否挂载会话记忆
     * @return 客户端
     * @throws BusinessException 用户未配置模型或密钥时抛出 {@link ErrorCode#USER_LLM_NOT_CONFIGURED}
     */
    private ChatClient createUserClient(Long userId, boolean withMemory) {
        ResolvedModelConfiguration resolved = configurationReader.find(CurrentActor.of(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_LLM_NOT_CONFIGURED));
        ChatModel chatModel = clients.buildUserModel(resolved.model(), resolved.apiKey());
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (withMemory) {
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        log.info("[ModelGateway] created ChatClient userId={} memory={} model={}",
                userId, withMemory, resolved.model().apiModelName());
        return builder.build();
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

    private static Long requireUserId(CurrentActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return actor.userId();
    }

    /**
     * 应用系统提示词。
     *
     * @param spec        客户端请求规范
     * @param promptName  提示词名称
     * @param promptVars  提示词变量
     * @return 应用系统提示词后的客户端请求规范
     */
    private ChatClientRequestSpec applySystemPrompt(ChatClientRequestSpec spec, String promptName,
            Map<String, Object> promptVars) {
        if (!StringUtils.hasText(promptName)) {
            return spec;
        }
        String template = promptLoader.load(promptName);
        Map<String, Object> variables = promptVars == null ? Collections.emptyMap() : promptVars;
        return spec.system(renderPlaceholders(template, variables));
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

    private static String cacheKey(Long userId, boolean memory) {
        return userId + (memory ? ":memory" : ":plain");
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
