package com.zdmj.common.util;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Component
@Slf4j
public class ChatUtil {

    private final ChatClient chatClient;

    /**
     * 聊天
     * 
     * @param message 消息
     * @return 回复
     */
    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
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
}
