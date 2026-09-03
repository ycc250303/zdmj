package com.zdmj.common.ai;

import java.util.Collections;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Component
public class ChatUtil {

    /**
     * 仅结构化调用附带 JSON Mode。请求 options 会与 ChatModel 默认项（model / extraBody）合并，
     * 不改缓存的 ChatClient，因此 SSE 对话不受影响。DeepSeek 不支持 json_schema。
     */
    private static final OpenAiChatOptions JSON_OBJECT_OPTIONS = OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
            .build();

    private final PromptUtil promptUtil;

    private final UserLlmRouter userLlmRouter;

    /**
     * 单次对话（按用户路由模型）。
     */
    public String chatOnce(Long userId, String userMessage, String promptName, Map<String, Object> promptVars) {
        requireUserId(userId);
        return applySystemPrompt(userLlmRouter.getChatClient(userId).prompt(), promptName, promptVars)
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 单次结构化对话：JSON Mode + {@code ChatClient.entity()}，解析失败直接抛出异常。
     */
    public <T> T chatStructuredOnce(Long userId, String userMessage, String promptName, Map<String, Object> promptVars,
            Class<T> outputType) {
        requireUserId(userId);
        return invokeStructured(
                applySystemPrompt(userLlmRouter.getChatClient(userId).prompt(), promptName, promptVars),
                userMessage, outputType);
    }

    /**
     * 使用平台指定模型进行单次结构化对话（忽略用户 LLM 配置）。
     */
    public <T> T chatStructuredOnceWithPlatformModel(String userMessage, String promptName,
            Map<String, Object> promptVars, Class<T> outputType, ModelEnum model) {
        return invokeStructured(
                applySystemPrompt(userLlmRouter.getPlatformChatClient(model).prompt(), promptName, promptVars),
                userMessage, outputType);
    }

    /**
     * 在会话中流式对话（按用户路由模型）。
     */
    public Flux<String> chatStreamInConversation(Long userId, Long conversationId, String userMessage,
            String promptName, Map<String, Object> promptVars) {
        if (conversationId == null) {
            throw new IllegalArgumentException("Conversation ID cannot be null");
        }
        requireUserId(userId);
        return applySystemPrompt(
                userLlmRouter.getChatClientWithMemory(userId).prompt()
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(conversationId))),
                promptName, promptVars)
                .user(userMessage)
                .stream()
                .content();
    }

    /**
     * JSON Mode + {@code .entity()}。Schema 由 ChatClient 追加到 user 消息。
     * DeepSeek JSON Mode 要求 prompt 含 "json"。
     * 
     * @param spec 聊天客户端请求规范
     * @param userMessage 用户消息
     * @param outputType 输出类型
     * @return 结构化输出结果
     */
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

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
    }

    /**
     * 应用系统提示词。
     * 
     * @param spec       聊天客户端请求规范
     * @param promptName 提示词名称
     * @param promptVars 提示词变量
     * @return 应用系统提示词后的聊天客户端请求规范
     */
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
     * 用变量值替换提示词中的占位符。支持 {@code ${key}} 与 {@code {key}}，仅替换 map 中声明的 key，
     * 避免 Spring AI {@code PromptTemplate}（StringTemplate）将 JSON 花括号或
     * {@code default} 等保留字误解析。
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
            String t = text == null ? "" : text.trim();
            if (t.startsWith("```json")) {
                t = t.substring(7).trim();
            } else if (t.startsWith("```")) {
                t = t.substring(3).trim();
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3).trim();
            }
            return t;
        }
    }
}
