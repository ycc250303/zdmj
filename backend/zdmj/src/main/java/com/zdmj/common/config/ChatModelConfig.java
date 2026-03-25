package com.zdmj.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatModelConfig {

    /**
     * Spring AI 的 {@link JdbcChatMemoryRepository} 在运行时会查询/插入
     * `SPRING_AI_CHAT_MEMORY` 表（包含 `conversation_id/content/type/"timestamp"` 等字段）。
     * 默认情况下（非内嵌数据库）不会自动建表，因此这里兜底确保表结构存在。
     */
    private void ensureChatMemorySchema(JdbcTemplate jdbcTemplate) {
        // 注意："timestamp" 使用双引号保持与 Spring AI 生成 SQL 中的标识符一致。
        String createTableSql = ""
                + "CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY ("
                + "  conversation_id VARCHAR(36) NOT NULL,"
                + "  content TEXT NOT NULL,"
                + "  type VARCHAR(10) NOT NULL,"
                + "  \"timestamp\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ");";
        jdbcTemplate.execute(createTableSql);

        // 为了兼容“表存在但列缺失”的情况，这里补齐必要列。
        // 说明：如果表里已经有数据，ADD COLUMN ... DEFAULT ... 通常会成功填充现有行。
        jdbcTemplate.execute("ALTER TABLE SPRING_AI_CHAT_MEMORY ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(36);");
        jdbcTemplate.execute("ALTER TABLE SPRING_AI_CHAT_MEMORY ADD COLUMN IF NOT EXISTS content TEXT;");
        jdbcTemplate.execute("ALTER TABLE SPRING_AI_CHAT_MEMORY ADD COLUMN IF NOT EXISTS type VARCHAR(10);");
        jdbcTemplate.execute("ALTER TABLE SPRING_AI_CHAT_MEMORY ADD COLUMN IF NOT EXISTS \"timestamp\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP;");

        String createIndexSql = ""
                + "CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conv_ts"
                + "  ON SPRING_AI_CHAT_MEMORY(conversation_id, \"timestamp\");";
        jdbcTemplate.execute(createIndexSql);
    }

    /**
     * 聊天记忆库
     * 
     * @param jdbcTemplate JdbcTemplate
     * @return JdbcChatMemoryRepository
     */
    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        ensureChatMemorySchema(jdbcTemplate);
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

    /**
     * 聊天客户端
     * 
     * @param model      ChatModel
     * @param chatMemory ChatMemory
     * @return ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
