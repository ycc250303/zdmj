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
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.RedisCacheUtil;
import com.zdmj.common.util.RedisConstants;
import com.zdmj.conversationService.dto.MessagesDTO;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.mapper.MessagesMapper;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.service.MessageService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 消息服务实现类
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessagesMapper, Messages> implements MessageService {

    private final ConversationMapper conversationMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatUtil chatUtil;
    private final ChatClient chatClient;
    private final RedisCacheUtil redisCacheUtil;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String defaultModel;

    // 流式输出超时时间（5分钟）
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    public MessageServiceImpl(ConversationMapper conversationMapper, ChatUtil chatUtil,
            ChatClient chatClient, RedisCacheUtil redisCacheUtil) {
        this.conversationMapper = conversationMapper;
        this.chatUtil = chatUtil;
        this.chatClient = chatClient;
        this.redisCacheUtil = redisCacheUtil;
    }

    @Override
    @Transactional
    public Flux<ServerSentEvent<String>> createMessage(MessagesDTO messagesDTO, Long conversationId) {
        Long userId = UserHolder.requireUserId();

        // 验证会话是否存在且属于当前用户（带缓存）
        Conversations conversation = getConversationById(conversationId);
        if (conversation == null) {
            return Flux.error(new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        }
        if (!conversation.getUserId().equals(userId)) {
            return Flux.error(new BusinessException(ErrorCode.NO_PERMISSION));
        }

        // 验证消息内容
        if (messagesDTO.getContent() == null || messagesDTO.getContent().trim().isEmpty()) {
            return Flux.error(new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED));
        }

        // 获取当前消息序号（会话中已有消息数 + 1）
        int currentSequence = conversation.getMessageCount() + 1;

        // 1. 创建并保存用户消息
        Messages userMessage = createUserMessage(conversationId, userId, messagesDTO.getContent(), currentSequence);
        boolean userMessageSaved = save(userMessage);
        if (!userMessageSaved) {
            return Flux.error(new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED));
        }

        // 2. 创建AI消息记录（初始content为空）
        Messages aiMessage = createAssistantMessage(conversationId, userId, "", currentSequence + 1);
        boolean aiMessageSaved = save(aiMessage);
        if (!aiMessageSaved) {
            return Flux.error(new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED));
        }

        // 3. 初始化Redis缓存
        redisCacheUtil.initStreamingMessage(aiMessage.getId());

        // 4. 获取历史消息（用于构建对话上下文）
        List<Messages> historyMessages = getMessagesByConversationId(conversationId, 1, 20);

        // 5. 构建Prompt（包含历史消息和当前用户消息）
        Prompt prompt = buildPromptWithHistory(conversation, historyMessages, messagesDTO.getContent());

        // 6. 启动流式调用并返回SSE Flux
        AtomicReference<String> fullContent = new AtomicReference<>("");
        String messageId = "msg-" + aiMessage.getId();
        long created = System.currentTimeMillis() / 1000;
        String model = conversation.getModel() != null ? conversation.getModel() : "qwen";

        return chatUtil.stream(chatClient, prompt)
                .timeout(STREAM_TIMEOUT)
                .doOnNext(chunk -> {
                    // 追加chunk到Redis缓存
                    redisCacheUtil.saveStreamingChunk(aiMessage.getId(), chunk);
                    // 累积完整内容
                    fullContent.updateAndGet(current -> current + chunk);
                })
                .map(chunk -> {
                    // 将chunk包装成OpenAI兼容的JSON格式，以便Apifox自动合并
                    String jsonData = buildOpenAIChunkJson(messageId, created, model, chunk);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .event("chunk")
                            .build();
                })
                .doOnComplete(() -> {
                    // 流式完成后更新PostgreSQL和Redis
                    try {
                        String finalContent = fullContent.get();
                        aiMessage.setContent(finalContent);
                        updateById(aiMessage);

                        // 更新Redis状态为completed
                        redisCacheUtil.markStreamingComplete(aiMessage.getId(), finalContent);

                        // 更新会话统计信息
                        conversation.setMessageCount(conversation.getMessageCount() + 2);
                        conversation.setLastMessageAt(DateTimeUtil.now());
                        conversation.setUpdatedAt(DateTimeUtil.now());
                        conversationMapper.updateById(conversation);

                        // 清除缓存
                        clearConversationCache(conversationId);

                        // 如果是第一条消息，异步生成会话标题
                        boolean isFirstMessage = conversation.getMessageCount() == 2;
                        if (isFirstMessage) {
                            generateAndSetTitleAsync(conversation, messagesDTO.getContent());
                        }
                    } catch (Exception e) {
                        log.error("流式输出完成后的处理失败: messageId={}", aiMessage.getId(), e);
                    }
                })
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .data("[DONE]")
                        .event("done")
                        .build()))
                .onErrorResume(error -> {
                    log.error("流式输出失败: messageId={}", aiMessage.getId(), error);
                    // 标记Redis状态为failed
                    redisCacheUtil.markStreamingFailed(aiMessage.getId());
                    // 返回错误事件
                    return Mono.just(ServerSentEvent.<String>builder()
                            .data("错误: " + error.getMessage())
                            .event("error")
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建用户消息
     */
    private Messages createUserMessage(Long conversationId, Long userId, String content, int sequence) {
        Messages message = new Messages();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(MessageRole.USER.getCode());
        message.setContent(content);
        message.setSequence(sequence);
        message.setMetadata(buildMessageMetadata(null, null));
        message.setCreatedAt(DateTimeUtil.now());
        message.setUpdatedAt(DateTimeUtil.now());
        return message;
    }

    /**
     * 创建AI助手消息
     */
    private Messages createAssistantMessage(Long conversationId, Long userId, String content, int sequence) {
        Messages message = new Messages();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(MessageRole.ASSISTANT.getCode());
        message.setContent(content);
        message.setSequence(sequence);
        // TODO: 从LLM响应中提取token使用情况、生成时间等信息
        message.setMetadata(buildMessageMetadata(null, null));
        message.setCreatedAt(DateTimeUtil.now());
        message.setUpdatedAt(DateTimeUtil.now());
        return message;
    }

    /**
     * 构建消息元数据（JSONB格式）
     */
    private String buildMessageMetadata(Map<String, Object> tokens, Long generationTimeMs) {
        try {
            Map<String, Object> metadata = new HashMap<>();

            // 模型信息
            metadata.put("model", defaultModel);

            // Token使用情况
            if (tokens != null) {
                metadata.put("tokens", tokens);
            } else {
                // TODO: 从LLM响应中获取实际的token使用情况
                metadata.put("tokens", Map.of(
                        "prompt", 0,
                        "completion", 0,
                        "total", 0));
            }

            // 生成时间
            if (generationTimeMs != null) {
                metadata.put("generation_time_ms", generationTimeMs);
            }

            // 完成原因
            metadata.put("finish_reason", "stop");

            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("构建消息元数据失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 构建包含历史消息的Prompt
     */
    private Prompt buildPromptWithHistory(Conversations conversation, List<Messages> historyMessages,
            String currentUserMessage) {
        List<Message> messages = new ArrayList<>();

        // 添加系统消息（如果有自定义系统消息，可以从conversation.config中获取）
        String systemMessage = "你是一位专业的软件工程专业大学生求职助手，请根据用户的问题，给出详细的回答，并给出相应的建议。";
        messages.add(new SystemMessage(systemMessage));

        // 添加历史消息
        for (Messages msg : historyMessages) {
            if (MessageRole.USER.getCode().equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (MessageRole.ASSISTANT.getCode().equals(msg.getRole())) {
                messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
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

    /**
     * 异步生成会话标题（使用Reactor异步执行）
     */
    private void generateAndSetTitleAsync(Conversations conversation, String firstMessage) {
        Mono.fromCallable(() -> {
            try {
                String titlePrompt = "请根据以下用户消息生成一个简洁的会话标题（不超过20个字符，不要包含引号）：\n" + firstMessage;
                Prompt prompt = new Prompt(new UserMessage(titlePrompt));

                String generatedTitle = chatUtil.callForContent(chatClient, prompt);
                if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                    // 清理标题（移除引号、换行等）
                    generatedTitle = generatedTitle.trim()
                            .replace("\"", "")
                            .replace("'", "")
                            .replace("\n", " ")
                            .trim();
                    // 限制长度
                    if (generatedTitle.length() > 20) {
                        generatedTitle = generatedTitle.substring(0, 20);
                    }
                    conversation.setTitle(generatedTitle);
                    conversationMapper.updateById(conversation);
                    // 清除缓存
                    clearConversationCache(conversation.getId());
                    log.info("生成会话标题成功: conversationId={}, title={}", conversation.getId(), generatedTitle);
                    return generatedTitle;
                }
            } catch (Exception e) {
                log.warn("生成会话标题失败: conversationId={}", conversation.getId(), e);
                // 如果生成失败，使用默认标题
                if (firstMessage != null && firstMessage.length() > 0) {
                    String title = firstMessage.trim();
                    if (title.length() > 20) {
                        title = title.substring(0, 30) + "...";
                    }
                    conversation.setTitle(title);
                    conversationMapper.updateById(conversation);
                    // 清除缓存
                    clearConversationCache(conversation.getId());
                }
            }
            return null;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> log.debug("标题生成完成: conversationId={}", conversation.getId()),
                        error -> log.error("标题生成异常: conversationId={}", conversation.getId(), error));
    }

    @Override
    public List<Messages> getMessagesByConversationId(Long conversationId, Integer page, Integer size) {
        Long userId = UserHolder.requireUserId();

        // 验证会话是否存在且属于当前用户（带缓存）
        Conversations conversation = getConversationById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 计算分页参数
        int limit = size != null && size > 0 ? size : 20;
        int offset = (page != null && page > 0 ? page - 1 : 0) * limit;

        return baseMapper.selectMessagesByConversationId(conversationId, limit, offset);
    }

    @Override
    public Messages getById(Long messageId) {
        Long userId = UserHolder.requireUserId();

        Messages message = baseMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        if (!message.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        return message;
    }

    @Override
    public Flux<ServerSentEvent<String>> getStreamWithRecover(Long messageId, boolean recover) {
        Long userId = UserHolder.requireUserId();

        // 验证消息是否存在且属于当前用户
        Messages message = baseMapper.selectById(messageId);
        if (message == null) {
            return Flux.error(new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        }
        if (!message.getUserId().equals(userId)) {
            return Flux.error(new BusinessException(ErrorCode.NO_PERMISSION));
        }

        // 验证消息必须是AI助手消息
        if (!MessageRole.ASSISTANT.getCode().equals(message.getRole())) {
            return Flux.error(new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        }

        // 从Redis获取流式消息状态
        RedisCacheUtil.StreamingMessage streamingMessage = redisCacheUtil.getStreamingMessage(messageId);

        if (streamingMessage == null) {
            // 如果Redis中没有缓存，说明流式输出已完成或从未开始
            // 直接从数据库返回完整内容（包装成OpenAI格式）
            String content = message.getContent() != null ? message.getContent() : "";
            String openAIMessageId = "msg-" + messageId;
            long created = System.currentTimeMillis() / 1000;
            Conversations conv = conversationMapper.selectById(message.getConversationId());
            String model = conv != null && conv.getModel() != null ? conv.getModel() : "qwen";
            String jsonData = buildOpenAIChunkJson(openAIMessageId, created, model, content);
            return Flux.just(ServerSentEvent.<String>builder()
                    .data(jsonData)
                    .event("complete")
                    .build())
                    .concatWith(Flux.just(ServerSentEvent.<String>builder()
                            .data("[DONE]")
                            .event("done")
                            .build()));
        }

        // 如果状态是completed，返回完整内容（包装成OpenAI格式）
        if ("completed".equals(streamingMessage.getStatus())) {
            String content = streamingMessage.getContent() != null ? streamingMessage.getContent() : "";
            String openAIMessageId = "msg-" + messageId;
            long created = System.currentTimeMillis() / 1000;
            Conversations conv = conversationMapper.selectById(message.getConversationId());
            String model = conv != null && conv.getModel() != null ? conv.getModel() : "qwen";

            // 如果内容不为空，按chunk返回（兼容OpenAI格式）
            if (!content.isEmpty() && streamingMessage.getChunks() != null && !streamingMessage.getChunks().isEmpty()) {
                List<ServerSentEvent<String>> events = new ArrayList<>();
                for (String chunk : streamingMessage.getChunks()) {
                    String jsonData = buildOpenAIChunkJson(openAIMessageId, created, model, chunk);
                    events.add(ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .event("chunk")
                            .build());
                }
                return Flux.fromIterable(events)
                        .concatWith(Flux.just(ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .event("done")
                                .build()));
            } else {
                // 如果没有chunks，直接返回完整内容
                String jsonData = buildOpenAIChunkJson(openAIMessageId, created, model, content);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data(jsonData)
                        .event("complete")
                        .build())
                        .concatWith(Flux.just(ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .event("done")
                                .build()));
            }
        }

        // 如果状态是failed，返回错误
        if ("failed".equals(streamingMessage.getStatus())) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("流式输出失败")
                    .event("error")
                    .build());
        }

        // 如果状态是streaming，先推送已生成的内容，然后继续监听
        // 注意：由于流式输出已经在进行中，我们只能返回已生成的内容
        // 新的chunk会继续通过原来的流式输出推送
        String existingContent = streamingMessage.getContent() != null ? streamingMessage.getContent() : "";

        if (existingContent.isEmpty()) {
            // 如果还没有内容，返回等待事件
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("")
                    .event("waiting")
                    .build());
        }

        // 推送已生成的内容（按chunk推送，包装成OpenAI格式）
        List<ServerSentEvent<String>> events = new ArrayList<>();
        String openAIMessageId = "msg-" + messageId;
        long created = System.currentTimeMillis() / 1000;
        Conversations conv = conversationMapper.selectById(message.getConversationId());
        String model = conv != null && conv.getModel() != null ? conv.getModel() : "qwen";

        if (streamingMessage.getChunks() != null && !streamingMessage.getChunks().isEmpty()) {
            for (String chunk : streamingMessage.getChunks()) {
                String jsonData = buildOpenAIChunkJson(openAIMessageId, created, model, chunk);
                events.add(ServerSentEvent.<String>builder()
                        .data(jsonData)
                        .event("chunk")
                        .build());
            }
        } else {
            // 如果没有chunks，直接推送完整内容
            String jsonData = buildOpenAIChunkJson(openAIMessageId, created, model, existingContent);
            events.add(ServerSentEvent.<String>builder()
                    .data(jsonData)
                    .event("chunk")
                    .build());
        }

        // 注意：由于流式输出可能还在进行中，我们无法直接监听新的chunk
        // 前端需要定期轮询或重新建立连接来获取新内容
        // 这里返回已生成的内容，并提示前端继续等待
        return Flux.fromIterable(events)
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .data("")
                        .event("streaming")
                        .comment("流式输出进行中，请稍后刷新获取最新内容")
                        .build()));
    }

    @Override
    @Transactional
    public void delete(Long messageId) {
        // TODO: 待实现 - 消息删除功能
        // 需要实现：
        // 1. 验证消息是否存在且属于当前用户
        // 2. 删除消息（物理删除或逻辑删除）
        // 3. 更新会话的消息数量
        // 4. 如果删除的是最后一条消息，更新会话的lastMessageAt
        throw new BusinessException(ErrorCode.FEATURE_NOT_IMPLEMENTED);
    }

    @Override
    @Transactional
    public Messages editAndResend(Long messageId, String newContent) {
        // TODO: 待实现 - 编辑消息并重新发送功能
        // 需要实现：
        // 1. 验证消息是否存在且属于当前用户
        // 2. 验证消息必须是用户消息（role = USER）
        // 3. 删除该消息之后的所有消息（包括该消息对应的AI回复）
        // 4. 使用新内容重新创建用户消息
        // 5. 调用LLM生成新的AI回复
        // 6. 更新会话的消息数量和最后消息时间
        throw new BusinessException(ErrorCode.FEATURE_NOT_IMPLEMENTED);
    }

    @Override
    public Long quote(Long messageId, Long conversationId) {
        // TODO: 待实现 - 引用消息功能
        // 需要实现：
        // 1. 验证被引用的消息是否存在且属于当前用户
        // 2. 验证会话是否存在且属于当前用户
        // 3. 在消息的metadata中记录引用信息
        // 4. 返回引用消息的ID（或创建引用消息记录）
        throw new BusinessException(ErrorCode.FEATURE_NOT_IMPLEMENTED);
    }

    /**
     * 获取会话记录（带Redis缓存）
     * 
     * @param conversationId 会话ID
     * @return 会话对象，如果不存在返回null
     */
    private Conversations getConversationById(Long conversationId) {
        if (conversationId == null) {
            return null;
        }

        String cacheKey = RedisConstants.CONVERSATION_KEY + conversationId;

        // 先尝试从缓存获取
        Conversations cachedConversation = redisCacheUtil.get(cacheKey, Conversations.class);
        if (cachedConversation != null) {
            log.debug("从缓存获取会话: conversationId={}", conversationId);
            return cachedConversation;
        }

        // 缓存未命中，从数据库查询
        Conversations conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            // 保存到缓存
            redisCacheUtil.set(cacheKey, conversation, RedisConstants.CONVERSATION_TTL);
            log.debug("会话已缓存: conversationId={}", conversationId);
        } else {
            // 防止缓存穿透：设置空值标记
            redisCacheUtil.setNullValue(cacheKey, RedisConstants.CONVERSATION_TTL);
        }

        return conversation;
    }

    /**
     * 清除会话缓存
     * 
     * @param conversationId 会话ID
     */
    private void clearConversationCache(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        String cacheKey = RedisConstants.CONVERSATION_KEY + conversationId;
        redisCacheUtil.delete(cacheKey);
        // 同时清除空值标记
        redisCacheUtil.deleteNullValue(cacheKey);
        log.debug("已清除会话缓存: conversationId={}", conversationId);
    }

    /**
     * 构建OpenAI兼容的流式响应JSON格式
     * 用于Apifox等工具自动合并SSE消息
     * 
     * @param messageId 消息ID
     * @param created   创建时间戳（秒）
     * @param model     模型名称
     * @param content   内容chunk
     * @return JSON字符串
     */
    private String buildOpenAIChunkJson(String messageId, long created, String model, String content) {
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

    /**
     * 消息角色枚举
     */
    public enum MessageRole {
        /**
         * 用户消息
         */
        USER(1, "user"),

        /**
         * AI助手消息
         */
        ASSISTANT(2, "assistant"),

        /**
         * 系统消息
         */
        SYSTEM(3, "system");

        private final Integer code;
        private final String name;

        MessageRole(Integer code, String name) {
            this.code = code;
            this.name = name;
        }

        public Integer getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }
}
