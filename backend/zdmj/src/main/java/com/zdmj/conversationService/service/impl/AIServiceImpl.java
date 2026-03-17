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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.PromptTemplateUtil;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.service.AIService;
import com.zdmj.conversationService.service.ProjectRAGService;

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
    private final ProjectRAGService projectRAGService;
    private final PromptTemplateUtil promptTemplateUtil;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String defaultModel;

    // 流式输出超时时间（5分钟）
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    // Prompt 模板名称
    private static final String SYSTEM_PROMPT_TEMPLATE = "system";
    private static final String PROJECT_CONTEXT_INSTRUCTION_TEMPLATE = "project-context-instruction";

    public AIServiceImpl(ChatUtil chatUtil, ChatClient chatClient, ProjectRAGService projectRAGService,
            PromptTemplateUtil promptTemplateUtil) {
        this.chatUtil = chatUtil;
        this.chatClient = chatClient;
        this.projectRAGService = projectRAGService;
        this.promptTemplateUtil = promptTemplateUtil;
    }

    @Override
    public Prompt buildPromptWithHistory(Conversations conversation, List<Messages> historyMessages,
            String currentUserMessage) {
        List<Message> messages = new ArrayList<>();

        // 从文件加载系统消息
        String systemMessage;
        try {
            systemMessage = promptTemplateUtil.loadTemplate(SYSTEM_PROMPT_TEMPLATE);
        } catch (Exception e) {
            log.error("加载系统消息模板失败，使用默认消息", e);
            // 降级：使用简单的默认消息
            systemMessage = "你是一位专业的软件工程专业大学生求职助手，请根据用户的问题，给出详细的回答，并给出相应的建议。";
        }

        // 如果会话关联了项目，注入项目上下文（RAG）
        if (conversation.getProjectId() != null) {
            try {
                String projectContext = projectRAGService.retrieveProjectContext(
                        conversation.getProjectId(),
                        conversation.getUserId(),
                        currentUserMessage);
                // 将项目上下文添加到系统消息中
                if (projectContext != null && !projectContext.trim().isEmpty()) {
                    systemMessage = systemMessage + "\n\n" + projectContext;
                    // 在项目上下文后再次强调数据准确性要求（从文件加载）
                    try {
                        String projectContextInstruction = promptTemplateUtil
                                .loadTemplate(PROJECT_CONTEXT_INSTRUCTION_TEMPLATE);
                        systemMessage = systemMessage + "\n\n" + projectContextInstruction;
                    } catch (Exception e) {
                        log.warn("加载项目上下文说明模板失败，跳过", e);
                        // 如果加载失败，使用简单的提示
                        systemMessage = systemMessage + "\n\n【项目信息使用说明】\n" +
                                "以上是项目的完整上下文信息。请严格遵循以下原则：\n" +
                                "- 如果项目信息中没有提及具体数据，请明确说明\"项目信息中未包含此数据\"，不要编造。\n" +
                                "- 所有回答必须基于上述项目上下文，不得添加未提及的信息。";
                    }
                    log.debug("已注入项目上下文: projectId={}, userId={}",
                            conversation.getProjectId(), conversation.getUserId());
                }
            } catch (Exception e) {
                // 错误降级：记录日志但不影响对话流程
                log.error("检索项目上下文失败，将使用默认系统消息: projectId={}, userId={}",
                        conversation.getProjectId(), conversation.getUserId(), e);
            }
        }

        messages.add(new SystemMessage(systemMessage));

        // 添加历史消息
        // 角色常量：1=user, 2=assistant, 3=system
        for (Messages msg : historyMessages) {
            Integer role = msg.getRole();
            String content = msg.getContent();
            // 跳过内容为空的消息
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            if (role != null) {
                if (role == 1) { // USER
                    messages.add(new UserMessage(content));
                } else if (role == 2) { // ASSISTANT
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(content));
                }
                // role == 3 (SYSTEM) 已经在上面添加了系统消息，这里跳过
            }
        }

        // 添加当前用户消息
        messages.add(new UserMessage(currentUserMessage));

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
            java.util.function.BiConsumer<String, Map<String, Object>> onComplete,
            java.util.function.Consumer<Throwable> onError,
            java.util.function.Consumer<Map<String, Object>> onMetadata) {

        AtomicReference<String> fullContent = new AtomicReference<>("");
        long created = System.currentTimeMillis() / 1000;
        long startTime = System.currentTimeMillis();
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
                    // 流式完成后，使用非流式调用获取完整响应以提取元数据
                    // 注意：这会消耗额外的token，但可以获取完整的元数据
                    ChatResponse fullResponse = null;
                    try {
                        fullResponse = chatUtil.call(chatClient, prompt);
                    } catch (Exception e) {
                        log.warn("获取完整响应失败，无法提取元数据: {}", e.getMessage());
                    }

                    // 提取元数据
                    Map<String, Object> metadata = extractMetadata(fullResponse, actualModel, startTime);

                    // 执行元数据回调
                    if (onMetadata != null && metadata != null) {
                        onMetadata.accept(metadata);
                    }

                    // 执行完成回调（传递内容和元数据）
                    if (onComplete != null) {
                        onComplete.accept(fullContent.get(), metadata != null ? metadata : new HashMap<>());
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

    /**
     * 从ChatResponse中提取元数据
     * 
     * @param response  ChatResponse对象
     * @param model     模型名称
     * @param startTime 开始时间（毫秒）
     * @return 元数据Map
     */
    private Map<String, Object> extractMetadata(ChatResponse response, String model, long startTime) {
        Map<String, Object> metadata = new HashMap<>();

        if (response == null) {
            return metadata;
        }

        try {
            // 模型信息
            metadata.put("model", model);

            // 生成时间
            long generationTimeMs = System.currentTimeMillis() - startTime;
            metadata.put("generation_time_ms", generationTimeMs);

            // Token使用情况
            Map<String, Object> tokens = new HashMap<>();
            if (response.getMetadata() != null) {
                // 尝试从metadata中获取token信息
                Object usageObj = response.getMetadata().get("usage");
                if (usageObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) usageObj;
                    tokens.put("prompt", usage.getOrDefault("prompt_tokens", 0));
                    tokens.put("completion", usage.getOrDefault("completion_tokens", 0));
                    tokens.put("total", usage.getOrDefault("total_tokens", 0));
                } else {
                    // 如果没有usage信息，尝试从其他字段获取
                    Object promptTokens = response.getMetadata().get("prompt_tokens");
                    Object completionTokens = response.getMetadata().get("completion_tokens");
                    Object totalTokens = response.getMetadata().get("total_tokens");

                    tokens.put("prompt", promptTokens != null ? promptTokens : 0);
                    tokens.put("completion", completionTokens != null ? completionTokens : 0);
                    tokens.put("total", totalTokens != null ? totalTokens : 0);
                }
            } else {
                // 如果没有metadata，设置默认值
                tokens.put("prompt", 0);
                tokens.put("completion", 0);
                tokens.put("total", 0);
            }
            metadata.put("tokens", tokens);

            // 完成原因
            String finishReason = "stop";
            if (response.getResult() != null && response.getResult().getMetadata() != null) {
                Object finishReasonObj = response.getResult().getMetadata().get("finish_reason");
                if (finishReasonObj != null) {
                    finishReason = finishReasonObj.toString();
                }
            }
            metadata.put("finish_reason", finishReason);

        } catch (Exception e) {
            log.warn("提取元数据失败: {}", e.getMessage());
            // 即使提取失败，也返回基本的元数据
            metadata.put("model", model);
            metadata.put("generation_time_ms", System.currentTimeMillis() - startTime);
            metadata.put("tokens", Map.of("prompt", 0, "completion", 0, "total", 0));
            metadata.put("finish_reason", "stop");
        }

        return metadata;
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
