package com.zdmj.common.util;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
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
     * 聊天
     * 
     * @param message 消息
     * @return 回复
     */
    public String chat(String message) {
        return chat(null, message, null);
    }

    /**
     * 聊天
     * 
     * @param message    消息
     * @param promptName 提示词名称
     * @return 回复
     */
    public String chat(String message, String promptName) {
        return chat(null, message, promptName);
    }

    /**
     * 聊天流
     * 
     * @param conversationId 会话ID
     * @param message        消息
     * @param promptName     提示词名称
     * @return 回复流
     */
    public String chat(Long conversationId, String message, String promptName) {
        ChatClientRequestSpec spec = chatClient.prompt();
        spec = applyMemory(spec, conversationId);
        if (promptName != null && !promptName.isBlank()) {
            spec = spec.system(promptUtil.load(promptName));
        }
        return spec.user(message).call().content();
    }

    /**
     * 聊天流
     * 
     * @param message 消息
     * @return 回复流
     */
    public Flux<String> chatStream(String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    /**
     * 聊天流
     * 
     * @param message    消息
     * @param promptName 提示词名称
     * @return 回复流
     */
    public Flux<String> chatStream(String message, String promptName) {
        return chatStream(null, message, promptName);
    }

    /**
     * 聊天流
     * 
     * @param conversationId 会话ID
     * @param message        消息
     * @return 回复流
     */
    public Flux<String> chatStream(Long conversationId, String message) {
        return chatStream(conversationId, message, PromptUtil.PromptNames.SYSTEM);
    }

    /**
     * 聊天流
     * 
     * @param conversationId 会话ID
     * @param message        消息
     * @param promptName     提示词名称
     * @return 回复流
     */
    public Flux<String> chatStream(Long conversationId, String message, String promptName) {
        ChatClientRequestSpec spec = chatClient.prompt();
        spec = applyMemory(spec, conversationId);
        if (promptName != null && !promptName.isBlank()) {
            spec = spec.system(promptUtil.load(promptName));
        }
        return spec.user(message).stream().content();
    }

    /**
     * 应用记忆
     * 
     * @param spec           请求规格
     * @param conversationId 会话ID
     * @return 请求规格
     */
    private ChatClientRequestSpec applyMemory(ChatClientRequestSpec spec,
            Long conversationId) {
        if (conversationId == null) {
            return spec;
        }
        String threadId = String.valueOf(conversationId);
        return spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, threadId));
    }
}
