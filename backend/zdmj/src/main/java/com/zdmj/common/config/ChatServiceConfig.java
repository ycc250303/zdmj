package com.zdmj.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chat")
public class ChatServiceConfig {
    @Bean
    public ChatClient chatClient(ChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem("你是一位专业的软件工程专业大学生求职助手，请根据用户的问题，给出详细的回答，并给出相应的建议。")

                .build();
    }
}
