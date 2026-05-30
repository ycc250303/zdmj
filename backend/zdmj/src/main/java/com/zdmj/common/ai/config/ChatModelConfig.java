package com.zdmj.common.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatModelConfig {

    /**
     * 聊天记忆库
     * 
     * @param jdbcTemplate JdbcTemplate
     * @return JdbcChatMemoryRepository
     */
    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .build();
    }

    /**
     * 聊天记忆
     * 
     * @param chatMemoryRepository JdbcChatMemoryRepository
     * @return ChatMemory
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(40)
                .build();
    }

    // /**
    //  * 无会话记忆的 ChatClient：能力画像、岗位识别等单次/结构化调用使用，不写 SPRING_AI_CHAT_MEMORY。
    //  */
    // @Primary
    // @Bean
    // public ChatClient chatClient(ChatModel model) {
    //     return ChatClient.builder(model).build();
    // }

    // /**
    //  * 带 JDBC 会话记忆的 ChatClient：仅对话接口（chatInConversation / chatStreamInConversation）使用。
    //  */
    // @Bean
    // public ChatClient chatClientWithMemory(ChatModel model, ChatMemory chatMemory) {
    //     return ChatClient.builder(model)
    //             .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    //             .build();
    // }
}
