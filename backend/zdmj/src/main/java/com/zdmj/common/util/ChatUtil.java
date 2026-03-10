package com.zdmj.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Chat 公共工具类
 * 提供 ChatClient 相关的工具方法
 * 适用于 Spring AI 1.1.0-M6 版本
 */
@Slf4j
@Component
public class ChatUtil {
    private final ObjectMapper objectMapper;

    public ChatUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 非流式调用：返回完整响应
     * 
     * @param chatClient ChatClient 实例
     * @param prompt     Prompt 对象
     * @return ChatResponse
     */
    public ChatResponse call(ChatClient chatClient, Prompt prompt) {
        try {
            return chatClient.prompt(prompt).call().entity(ChatResponse.class);
        } catch (Exception e) {
            log.error("ChatClient 调用失败", e);
            throw new RuntimeException("ChatClient 调用失败", e);
        }
    }

    /**
     * 非流式调用：返回文本内容
     * 
     * @param chatClient ChatClient 实例
     * @param prompt     Prompt 对象
     * @return 文本内容
     */
    public String callForContent(ChatClient chatClient, Prompt prompt) {
        ChatResponse response = call(chatClient, prompt);
        return response.getResult().getOutput().getText();
    }

    /**
     * 流式调用：返回文本流
     * 
     * @param chatClient ChatClient 实例
     * @param prompt     Prompt 对象
     * @return Flux<String>，每个元素是增量文本
     */
    public Flux<String> stream(ChatClient chatClient, Prompt prompt) {
        try {
            return chatClient.prompt(prompt)
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("ChatClient 流式调用失败", e);
            return Flux.error(new RuntimeException("ChatClient 流式调用失败", e));
        }
    }

    /**
     * 解析 JSON 输出为对象
     * 
     * @param jsonContent JSON 字符串
     * @param clazz       目标类型
     * @return 解析后的对象
     * @param <T> 目标类型
     */
    public <T> T parseJson(String jsonContent, Class<T> clazz) {
        try {
            return objectMapper.readValue(jsonContent, clazz);
        } catch (JsonProcessingException e) {
            log.error("解析 JSON 失败: {}", jsonContent, e);
            throw new RuntimeException("解析 JSON 失败", e);
        }
    }

    /**
     * 从 ChatResponse 解析 JSON 输出为对象
     * 
     * @param response ChatResponse
     * @param clazz    目标类型
     * @return 解析后的对象
     * @param <T> 目标类型
     */
    public <T> T parseResponse(ChatResponse response, Class<T> clazz) {
        String content = response.getResult().getOutput().getText();
        return parseJson(content, clazz);
    }

    /**
     * 构建带系统消息的 Prompt
     * 
     * @param systemMessage 系统消息
     * @param userMessage   用户消息
     * @return Prompt 对象
     */
    public Prompt buildPromptWithSystem(String systemMessage, String userMessage) {
        return new Prompt(
                new SystemMessage(systemMessage),
                new UserMessage(userMessage));
    }

    /**
     * 构建带系统消息和上下文的 Prompt
     * 
     * @param systemMessage  系统消息
     * @param contextMessage 上下文消息
     * @param userMessage    用户消息
     * @return Prompt 对象
     */
    public Prompt buildPromptWithSystemAndContext(String systemMessage, String contextMessage, String userMessage) {
        return new Prompt(
                new SystemMessage(systemMessage),
                new UserMessage(contextMessage),
                new UserMessage(userMessage));
    }

    /**
     * 使用 PromptTemplate 构建 Prompt
     * 
     * @param template  模板内容
     * @param variables 模板变量
     * @return Prompt 对象
     */
    public Prompt buildPromptFromTemplate(String template, Map<String, Object> variables) {
        PromptTemplate promptTemplate = new PromptTemplate(template);
        return promptTemplate.create(variables);
    }
}
