package com.zdmj.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "chat")
public class ChatServiceConfig {

    private final ResourceLoader resourceLoader;

    public ChatServiceConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public ChatClient chatClient(ChatModel model) {
        // 从文件加载系统消息
        String systemMessage = loadSystemPrompt();
        return ChatClient.builder(model)
                .defaultSystem(systemMessage)
                .build();
    }

    /**
     * 加载系统消息模板
     * 
     * @return 系统消息内容
     */
    private String loadSystemPrompt() {
        try {
            Resource resource = resourceLoader.getResource("classpath:prompts/system.md");
            if (!resource.exists()) {
                log.warn("系统消息模板文件不存在，使用默认消息");
                return getDefaultSystemMessage();
            }
            String content = StreamUtils.copyToString(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8);
            log.debug("加载系统消息模板成功");
            return content;
        } catch (Exception e) {
            log.error("加载系统消息模板失败，使用默认消息", e);
            return getDefaultSystemMessage();
        }
    }

    /**
     * 获取默认系统消息（降级方案）
     * 
     * @return 默认系统消息
     */
    private String getDefaultSystemMessage() {
        return "你是一位专业的软件工程专业大学生求职助手，请根据用户的问题，给出详细的回答，并给出相应的建议。";
    }
}
