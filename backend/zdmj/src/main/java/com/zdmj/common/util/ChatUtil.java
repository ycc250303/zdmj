package com.zdmj.common.util;

import java.util.Collections;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Component
@Slf4j
public class ChatUtil {

    private final ChatClient chatClient;
    private final PromptUtil promptUtil;

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
     * @param userMessage 用户消息
     * @param promptName  提示词名称
     * @param promptVars  提示词模板变量
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
        ChatClientRequestSpec spec = chatClient.prompt();
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
        ChatClientRequestSpec spec = chatClient.prompt()
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
        if (promptName == null && promptName.isBlank()) {
            return spec;
        }
        String template = promptUtil.load(promptName);
        Map<String, Object> variables = promptVars == null ? Collections.emptyMap() : promptVars;
        if (variables.isEmpty()) {
            return spec.system(template);
        }
        PromptTemplate promptTemplate = new PromptTemplate(template);
        return spec.system(promptTemplate.render(variables));
    }
}
