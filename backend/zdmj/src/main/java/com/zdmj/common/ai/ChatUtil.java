package com.zdmj.common.ai;

import java.util.Collections;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.zdmj.common.context.UserHolder;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Component
public class ChatUtil {

    private final PromptUtil promptUtil;

    private final UserLlmRouter userLlmRouter;

    /**
     * 单次对话
     * 
     * @param userMessage 用户消息
     * @param promptName  提示词名称
     * @param promptVars  提示词模板变量
     * @return 对话内容
     */
    public String chatOnce(String userMessage, String promptName, Map<String, Object> promptVars) {
        ChatClientRequestSpec spec = buildSpecWithoutMemory(promptName, promptVars);
        return spec.user(userMessage).call().content();
    }

    /**
     * 单次结构化对话：在 user 侧附加 JSON Schema，只调用一次模型，解析失败直接抛出异常。
     *
     * @param userMessage 用户消息（如简历全文）
     * @param promptName  提示词名称（system）
     * @param promptVars  提示词模板变量
     * @param outputType  输出 POJO 类型
     */
    public <T> T chatStructuredOnce(String userMessage, String promptName, Map<String, Object> promptVars,
            Class<T> outputType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);
        String userPayload = buildStructuredUserPayload(userMessage, converter.getFormat());
        String content = chatOnce(userPayload, promptName, promptVars);
        String cleaned = stripCodeFence(content);
        T parsed = converter.convert(cleaned);
        if (parsed == null) {
            throw new IllegalStateException("结构化输出解析结果为空");
        }
        return parsed;
    }

    /**
     * 使用平台指定模型进行单次结构化对话（忽略用户 LLM 配置）。
     */
    public <T> T chatStructuredOnceWithPlatformModel(String userMessage, String promptName,
            Map<String, Object> promptVars, Class<T> outputType, ModelEnum model) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);
        String userPayload = buildStructuredUserPayload(userMessage, converter.getFormat());
        ChatClientRequestSpec spec = userLlmRouter.getPlatformChatClient(model).prompt();
        spec = applySystemPrompt(spec, promptName, promptVars);
        String content = spec.user(userPayload).call().content();
        String cleaned = stripCodeFence(content);
        T parsed = converter.convert(cleaned);
        if (parsed == null) {
            throw new IllegalStateException("结构化输出解析结果为空");
        }
        return parsed;
    }

    /**
     * 构建结构化用户消息
     * 
     * @param userMessage 用户消息
     * @param schemaHint  JSON Schema 提示
     * @return 结构化用户消息
     */
    private String buildStructuredUserPayload(String userMessage, String schemaHint) {
        StringBuilder sb = new StringBuilder(userMessage == null ? "" : userMessage);
        sb.append("\n\n请严格按照以下 JSON Schema 输出（仅 JSON 对象，无 Markdown、无前后说明）：\n");
        sb.append(schemaHint);
        return sb.toString();
    }

    /**
     * 单次流式对话
     * 
     * @param userMessage 用户消息
     * @param promptName  提示词名称
     * @param promptVars  提示词模板变量
     * @return 对话内容
     */
    public Flux<String> chatStreamOnce(String userMessage, String promptName, Map<String, Object> promptVars) {
        ChatClientRequestSpec spec = buildSpecWithoutMemory(promptName, promptVars);
        return spec.user(userMessage).stream().content();
    }

    /**
     * 在会话中对话
     * 
     * @param conversationId 会话ID
     * @param userMessage    用户消息
     * @param promptName     提示词名称
     * @param promptVars     提示词模板变量
     * @return 对话内容
     */
    public String chatInConversation(Long conversationId, String userMessage, String promptName,
            Map<String, Object> promptVars) {
        ChatClientRequestSpec spec = buildSpecWithMemory(conversationId, promptName, promptVars);
        return spec.user(userMessage).call().content();
    }

    /**
     * 在会话中流式对话
     * 
     * @param conversationId 会话ID
     * @param userMessage    用户消息
     * @param promptName     提示词名称
     * @param promptVars     提示词模板变量
     * @return 对话内容
     */
    public Flux<String> chatStreamInConversation(Long conversationId, String userMessage, String promptName,
            Map<String, Object> promptVars) {
        ChatClientRequestSpec spec = buildSpecWithMemory(conversationId, promptName, promptVars);
        return spec.user(userMessage).stream().content();
    }

    /**
     * 构建不带记忆的请求
     * 
     * @param promptName      提示词名称
     * @param promptVariables 提示词模板变量
     * @return 请求
     */
    private ChatClientRequestSpec buildSpecWithoutMemory(String promptName, Map<String, Object> promptVars) {
        Long userId = UserHolder.getUserId();
        ChatClientRequestSpec spec = userLlmRouter.getChatClient(userId).prompt();
        return applySystemPrompt(spec, promptName, promptVars);
    }

    /**
     * 构建带记忆的请求
     * 
     * @param conversationId  会话ID
     * @param promptName      提示词名称
     * @param promptVariables 提示词模板变量
     * @return 请求
     */
    private ChatClientRequestSpec buildSpecWithMemory(Long conversationId, String promptName,
            Map<String, Object> promptVars) {
        if (conversationId == null) {
            throw new IllegalArgumentException("Conversation ID cannot be null");
        }
        Long userId = UserHolder.getUserId();
        ChatClientRequestSpec spec = userLlmRouter.getChatClientWithMemory(userId).prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(conversationId)));
        return applySystemPrompt(spec, promptName, promptVars);
    }

    /*
     * 应用系统提示词
     * 
     * @param spec 请求
     * 
     * @param promptName 提示词名称
     * 
     * @param promptVariables 提示词模板变量
     * 
     * @return 请求
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
     * 避免 Spring AI {@code PromptTemplate}（StringTemplate）将 JSON 花括号或 {@code default} 等保留字误解析。
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
     * 去除代码块
     * 
     * @param text 文本
     * @return 去除代码块后的文本
     */
    private String stripCodeFence(String text) {
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
