package com.zdmj.conversationService.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.service.AIService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI服务实现类
 * 负责处理与AI模型相关的逻辑
 */
@Slf4j
@Service
public class AIServiceImpl implements AIService {

    private final ChatUtil chatUtil;
    private final ChatClient chatClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String defaultModel;

    // 流式输出超时时间（5分钟）
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    // 默认系统消息
    private static final String DEFAULT_SYSTEM_MESSAGE = "你是一位专业的软件工程专业大学生求职助手，请根据用户的问题，给出详细的回答，并给出相应的建议。";

    public AIServiceImpl(ChatUtil chatUtil, ChatClient chatClient) {
        this.chatUtil = chatUtil;
        this.chatClient = chatClient;
    }

    @Override
    public Prompt buildPromptWithHistory(Conversations conversation, List<Messages> historyMessages,
            String currentUserMessage) {
        List<Message> messages = new ArrayList<>();

        // 添加系统消息（如果有自定义系统消息，可以从conversation.config中获取）
        String systemMessage = DEFAULT_SYSTEM_MESSAGE;
        messages.add(new SystemMessage(systemMessage));

        // 添加历史消息
        // 角色常量：1=user, 2=assistant, 3=system
        for (Messages msg : historyMessages) {
            Integer role = msg.getRole();
            if (role != null) {
                if (role == 1) { // USER
                    messages.add(new UserMessage(msg.getContent()));
                } else if (role == 2) { // ASSISTANT
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                }
                // role == 3 (SYSTEM) 已经在上面添加了系统消息，这里跳过
            }
        }

        // 添加当前用户消息
        messages.add(new UserMessage(currentUserMessage));

        // TODO: 如果会话关联了项目，注入项目上下文（RAG）
        // if (conversation.getProjectId() != null) {
        // // 获取项目信息并注入上下文
        // }

        // TODO: 如果会话关联了知识库，注入知识库上下文（RAG）
        // if (conversation.getContext() != null &&
        // !conversation.getContext().isEmpty()) {
        // // 解析context JSON，获取知识库ID，进行RAG检索
        // }

        return new Prompt(messages);
    }

    @Override
    public Flux<ServerSentEvent<String>> streamChat(Prompt prompt, String messageId, String model,
            java.util.function.Consumer<String> onChunk,
            java.util.function.Consumer<String> onComplete,
            java.util.function.Consumer<Throwable> onError) {

        AtomicReference<String> fullContent = new AtomicReference<>("");
        long created = System.currentTimeMillis() / 1000;
        String actualModel = model != null ? model : defaultModel;

        return chatUtil.stream(chatClient, prompt)
                .timeout(STREAM_TIMEOUT)
                .doOnNext(chunk -> {
                    // 累积完整内容
                    fullContent.updateAndGet(current -> current + chunk);
                    // 执行chunk回调
                    if (onChunk != null) {
                        onChunk.accept(chunk);
                    }
                })
                .map(chunk -> {
                    // 将chunk包装成OpenAI兼容的JSON格式
                    String jsonData = buildOpenAIChunkJson(messageId, created, actualModel, chunk);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .event("chunk")
                            .build();
                })
                .doOnComplete(() -> {
                    // 执行完成回调
                    if (onComplete != null) {
                        onComplete.accept(fullContent.get());
                    }
                })
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .data("[DONE]")
                        .event("done")
                        .build()))
                .onErrorResume(error -> {
                    // 判断是否为连接关闭错误（应用重启时的正常情况）
                    boolean isConnectionClosed = error instanceof reactor.netty.http.client.PrematureCloseException
                            || (error.getCause() instanceof reactor.netty.http.client.PrematureCloseException)
                            || (error.getMessage() != null
                                    && error.getMessage().contains("Connection prematurely closed"));

                    if (isConnectionClosed) {
                        // 连接关闭通常是应用重启或客户端断开，记录为警告
                        log.warn("流式输出连接关闭（可能是应用重启或客户端断开）: messageId={}", messageId);
                    } else {
                        // 其他错误记录为错误
                        log.error("流式输出失败: messageId={}", messageId, error);
                    }

                    // 执行错误回调
                    if (onError != null) {
                        onError.accept(error);
                    }

                    // 返回错误事件（如果是连接关闭，使用更友好的消息）
                    String errorMessage = isConnectionClosed
                            ? "连接已关闭"
                            : "错误: " + (error.getMessage() != null ? error.getMessage() : "未知错误");
                    return Mono.just(ServerSentEvent.<String>builder()
                            .data(errorMessage)
                            .event("error")
                            .build());
                });
    }

    @Override
    public String callForContent(Prompt prompt) {
        return chatUtil.callForContent(chatClient, prompt);
    }

    @Override
    public String buildOpenAIChunkJson(String messageId, long created, String model, String content) {
        try {
            Map<String, Object> chunk = new HashMap<>();
            chunk.put("id", messageId);
            chunk.put("object", "chat.completion.chunk");
            chunk.put("created", created);
            chunk.put("model", model);

            Map<String, Object> choice = new HashMap<>();
            choice.put("index", 0);

            Map<String, Object> delta = new HashMap<>();
            delta.put("content", content);
            choice.put("delta", delta);
            choice.put("finish_reason", null);

            List<Map<String, Object>> choices = new ArrayList<>();
            choices.add(choice);
            chunk.put("choices", choices);

            return objectMapper.writeValueAsString(chunk);
        } catch (Exception e) {
            log.error("构建OpenAI格式JSON失败", e);
            // 如果JSON构建失败，返回简单的文本格式
            return content;
        }
    }
}
