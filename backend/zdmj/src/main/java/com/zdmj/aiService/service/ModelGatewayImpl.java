package com.zdmj.aiService.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
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
import com.zdmj.aiService.api.StreamingModelRequest;
import com.zdmj.aiService.api.StructuredModelRequest;
import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.model.ModelCode;
import com.zdmj.aiService.provider.ModelPurpose;
import com.zdmj.aiService.provider.ResolvedModelConfiguration;
import com.zdmj.aiService.provider.UserModelConfigurationProvider;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * {@link ModelGateway} 的实现：按用户配置或平台模型构建客户端，完成提示词渲染、结构化输出和流式调用。
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

    private final PromptUtil promptUtil;
    private final UserModelConfigurationProvider configurationProvider;
    private final ModelClientConfiguration clients;
    private final ChatMemory chatMemory;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> platformClientCache = new ConcurrentHashMap<>();

    /**
     * 装配提示词、用户配置查询、供应商客户端和会话记忆。
     *
     * @param promptUtil              提示词加载
     * @param configurationProvider   用户模型配置查询
     * @param clients                 供应商客户端参数
     * @param chatMemory              会话记忆，由会话模块提供存储与窗口
     */
    public ModelGatewayImpl(PromptUtil promptUtil, UserModelConfigurationProvider configurationProvider,
            ModelClientConfiguration clients, ChatMemory chatMemory) {
        this.promptUtil = promptUtil;
        this.configurationProvider = configurationProvider;
        this.clients = clients;
        this.chatMemory = chatMemory;
    }

    @Override
    public String generate(CurrentActor actor, ModelRequest request) {
        requireUserId(actor);
        return applySystemPrompt(userChatClient(actor, false).prompt(), request.promptName(), request.promptVars())
                .user(request.message())
                .call()
                .content();
    }

    @Override
    public <T> T generateStructured(CurrentActor actor, StructuredModelRequest<T> request) {
        ChatClientRequestSpec spec;
        if (request.platformModel() != null) {
            spec = platformChatClient(request.platformModel()).prompt();
        } else {
            requireUserId(actor);
            spec = userChatClient(actor, false).prompt();
        }
        return invokeStructured(
                applySystemPrompt(spec, request.promptName(), request.promptVars()),
                request.message(),
                request.outputType());
    }

    @Override
    public Flux<String> stream(CurrentActor actor, StreamingModelRequest request) {
        if (request.conversationId() == null) {
            throw new IllegalArgumentException("Conversation ID cannot be null");
        }
        requireUserId(actor);
        return applySystemPrompt(
                userChatClient(actor, true).prompt()
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID,
                                String.valueOf(request.conversationId()))),
                request.promptName(),
                request.promptVars())
                .user(request.message())
                .stream()
                .content();
    }

    @Override
    public ModelCode resumeImportModel() {
        return clients.resumeImportModel();
    }

    @Override
    public List<ModelCode> listModels() {
        return Arrays.asList(ModelCode.values());
    }

    @Override
    public boolean platformFallbackEnabled() {
        return clients.platformFallbackEnabled();
    }

    @Override
    public void evict(CurrentActor actor) {
        Long userId = requireUserId(actor);
        clientCache.remove(cacheKey(userId, false));
        clientCache.remove(cacheKey(userId, true));
        log.info("[ModelGateway] evicted cache userId={}", userId);
    }

    @Override
    public void testConnection(String modelCode, String apiKey) {
        long startedAt = System.currentTimeMillis();
        try {
            ModelCode model = ModelCode.fromCode(modelCode);
            ChatClient.builder(clients.buildProbe(modelCode, apiKey))
                    .build()
                    .prompt()
                    .user("ping")
                    .call()
                    .content();
            log.info("[ModelGateway] testConnection ok modelCode={} apiModel={} elapsedMs={}",
                    modelCode, model.apiModelName(), System.currentTimeMillis() - startedAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ModelGateway] testConnection failed modelCode={} elapsedMs={}",
                    modelCode, System.currentTimeMillis() - startedAt, e);
            throw new BusinessException(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, e);
        }
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
     * 取得平台指定模型的客户端，忽略用户配置。
     *
     * @param model 平台模型
     * @return 无会话记忆的客户端
     */
    protected ChatClient platformChatClient(ModelCode model) {
        return platformClientCache.computeIfAbsent("platform:" + model.code(),
                key -> {
                    ChatClient client = ChatClient.builder(clients.buildPlatformModel(model)).build();
                    log.info("[ModelGateway] created platform ChatClient model={}", model.apiModelName());
                    return client;
                });
    }

    private ChatClient createUserClient(Long userId, boolean withMemory) {
        ResolvedModelConfiguration resolved = configurationProvider.resolve(
                CurrentActor.of(userId), ModelPurpose.USER_CHAT);
        ChatModel chatModel;
        boolean platformDefault;
        String modelName;
        if (resolved.configured()) {
            chatModel = clients.buildUserModel(resolved.model(), resolved.apiKey());
            platformDefault = false;
            modelName = resolved.model().apiModelName();
        } else {
            chatModel = clients.buildPlatformDefaultModel();
            platformDefault = true;
            modelName = clients.platformModelName();
        }
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (withMemory) {
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        log.info("[ModelGateway] created ChatClient userId={} memory={} platformDefault={} model={}",
                userId, withMemory, platformDefault, modelName);
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

    private ChatClientRequestSpec applySystemPrompt(ChatClientRequestSpec spec, String promptName,
            Map<String, Object> promptVars) {
        if (!StringUtils.hasText(promptName)) {
            return spec;
        }
        String template = promptUtil.load(promptName);
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
