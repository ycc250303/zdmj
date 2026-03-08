package com.zdmj.conversationService.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.RedisCacheUtil;
import com.zdmj.common.util.RedisConstants;
import com.zdmj.conversationService.dto.MessagesDTO;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.mapper.MessagesMapper;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.service.AIService;
import com.zdmj.conversationService.service.MessageService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 消息服务实现类
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessagesMapper, Messages> implements MessageService {

    private final ConversationMapper conversationMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final AIService aiService;
    private final RedisCacheUtil redisCacheUtil;

    public MessageServiceImpl(ConversationMapper conversationMapper, AIService aiService,
            RedisCacheUtil redisCacheUtil) {
        this.conversationMapper = conversationMapper;
        this.aiService = aiService;
        this.redisCacheUtil = redisCacheUtil;
    }

    @Override
    public Flux<ServerSentEvent<String>> createMessage(MessagesDTO messagesDTO, Long conversationId) {
        // 在主线程中获取并保存上下文信息，避免在异步回调中访问已清除的ThreadLocal
        final Long userId = UserHolder.requireUserId();

        // 验证会话是否存在且属于当前用户（带缓存）
        Conversations conversation = getConversationById(conversationId);
        if (conversation == null) {
            return Flux.error(new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        }
        if (!conversation.getUserId().equals(userId)) {
            return Flux.error(new BusinessException(ErrorCode.NO_PERMISSION));
        }

        // 保存conversation对象和conversationId到final变量，供异步回调使用
        final Conversations finalConversation = conversation;
        final Long finalConversationId = conversationId;

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

        // 保存userMessage对象到final变量，供异步回调使用
        final Messages finalUserMessage = userMessage;

        // 2. 创建AI消息记录（初始content为空）
        Messages aiMessage = createAssistantMessage(conversationId, userId, "", currentSequence + 1);
        boolean aiMessageSaved = save(aiMessage);
        if (!aiMessageSaved) {
            return Flux.error(new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED));
        }

        // 保存aiMessage对象到final变量，供异步回调使用
        final Messages finalAiMessage = aiMessage;

        // 3. 初始化Redis缓存
        redisCacheUtil.initStreamingMessage(aiMessage.getId());

        // 4. 获取历史消息（优先从缓存获取，用于构建对话上下文）
        // 直接使用 getMessagesByConversationId，它会自动处理缓存逻辑
        List<Messages> historyMessages = getMessagesByConversationId(conversationId, 1, 20);

        // 5. 构建Prompt（包含历史消息和当前用户消息）
        Prompt prompt = aiService.buildPromptWithHistory(conversation, historyMessages, messagesDTO.getContent());

        // 6. 启动流式调用并返回SSE Flux
        String messageId = "msg-" + aiMessage.getId();
        String model = conversation.getModel() != null ? conversation.getModel() : "qwen";

        return aiService.streamChat(prompt, messageId, model,
                // onChunk: 每个chunk的回调
                chunk -> {
                    // 追加chunk到Redis缓存
                    redisCacheUtil.saveStreamingChunk(finalAiMessage.getId(), chunk);
                },
                // onComplete: 完成时的回调
                // 注意：此回调在Reactor异步线程中执行，此时原始请求线程已结束，
                // UserHolder和SecurityContext已被清除，因此不能依赖它们
                finalContent -> {
                    // 流式完成后更新PostgreSQL和Redis
                    try {
                        finalAiMessage.setContent(finalContent);
                        updateById(finalAiMessage);

                        // 更新Redis状态为completed
                        redisCacheUtil.markStreamingComplete(finalAiMessage.getId(), finalContent);

                        // 更新会话统计信息（使用保存的finalConversation对象，不依赖ThreadLocal）
                        finalConversation.setMessageCount(finalConversation.getMessageCount() + 2);
                        finalConversation.setLastMessageAt(DateTimeUtil.now());
                        finalConversation.setUpdatedAt(DateTimeUtil.now());
                        conversationMapper.updateById(finalConversation);

                        // 更新消息缓存：追加新完成的消息到缓存
                        List<Messages> newMessages = new ArrayList<>();
                        newMessages.add(finalUserMessage);
                        newMessages.add(finalAiMessage);
                        updateCachedHistoryMessages(finalConversationId, newMessages);

                        // 清除会话缓存（因为会话信息已更新，但保留消息缓存）
                        String conversationCacheKey = RedisConstants.CONVERSATION_KEY + finalConversationId;
                        redisCacheUtil.delete(conversationCacheKey);
                        redisCacheUtil.deleteNullValue(conversationCacheKey);
                        log.debug("已清除会话缓存: conversationId={}", finalConversationId);
                    } catch (Exception e) {
                        log.error("流式输出完成后的处理失败: messageId={}", finalAiMessage.getId(), e);
                    }
                },
                // onError: 错误时的回调
                // 注意：此回调在Reactor异步线程中执行，此时原始请求线程已结束，
                // UserHolder和SecurityContext已被清除，因此不能依赖它们
                error -> {
                    // 判断是否为连接关闭错误（应用重启时的正常情况）
                    boolean isConnectionClosed = error instanceof reactor.netty.http.client.PrematureCloseException
                            || (error.getCause() instanceof reactor.netty.http.client.PrematureCloseException)
                            || (error.getMessage() != null
                                    && error.getMessage().contains("Connection prematurely closed"));

                    if (isConnectionClosed) {
                        // 连接关闭通常是应用重启或客户端断开，记录为警告
                        log.warn("流式输出连接关闭（可能是应用重启或客户端断开）: messageId={}", finalAiMessage.getId());
                        // 对于连接关闭，不标记为失败，因为可能是应用重启导致的
                        // 如果内容已经部分保存，可以尝试标记为完成
                        try {
                            RedisCacheUtil.StreamingMessage streamingMessage = redisCacheUtil
                                    .getStreamingMessage(finalAiMessage.getId());
                            if (streamingMessage != null && streamingMessage.getContent() != null
                                    && !streamingMessage.getContent().trim().isEmpty()) {
                                // 如果有部分内容，标记为完成
                                redisCacheUtil.markStreamingComplete(finalAiMessage.getId(),
                                        streamingMessage.getContent());
                                // 更新数据库（使用保存的finalAiMessage对象，不依赖ThreadLocal）
                                finalAiMessage.setContent(streamingMessage.getContent());
                                updateById(finalAiMessage);
                            } else {
                                // 如果没有内容，标记为失败
                                redisCacheUtil.markStreamingFailed(finalAiMessage.getId());
                            }
                        } catch (Exception e) {
                            log.warn("处理连接关闭时的状态更新失败: messageId={}", finalAiMessage.getId(), e);
                            redisCacheUtil.markStreamingFailed(finalAiMessage.getId());
                        }
                    } else {
                        // 其他错误记录为错误并标记为失败
                        log.error("流式输出失败: messageId={}", finalAiMessage.getId(), error);
                        redisCacheUtil.markStreamingFailed(finalAiMessage.getId());
                    }
                });
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

            // 模型信息（使用默认模型）
            metadata.put("model", "qwen-plus");

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

        // 优先从缓存获取消息列表
        // 注意：缓存最多保存最近 20 条消息，所以只对第一页且 size <= 20 的查询使用缓存
        if (page == null || page == 1) {
            List<Messages> cachedMessages = getCachedHistoryMessages(conversationId, limit);
            if (cachedMessages != null && !cachedMessages.isEmpty()) {
                // 缓存命中，直接返回（缓存已经是最近的消息，按 sequence 排序）
                log.debug("从缓存获取消息列表: conversationId={}, page={}, size={}, cachedCount={}",
                        conversationId, page, size, cachedMessages.size());
                return cachedMessages;
            }
        }

        // 缓存未命中或需要查询非第一页，从数据库查询
        List<Messages> messages = baseMapper.selectMessagesByConversationId(conversationId, limit, offset);

        // 如果是第一页且查询结果不为空，更新缓存（用于后续查询优化）
        if ((page == null || page == 1) && !messages.isEmpty()) {
            String cacheKey = RedisConstants.CONVERSATION_MESSAGES_KEY + conversationId;
            // 只缓存最近 20 条消息
            List<Messages> messagesToCache = messages.size() > 20
                    ? new ArrayList<>(messages.subList(0, 20))
                    : new ArrayList<>(messages);
            redisCacheUtil.set(cacheKey, messagesToCache, RedisConstants.CONVERSATION_MESSAGES_TTL);
            log.debug("缓存消息列表: conversationId={}, count={}", conversationId, messagesToCache.size());
        }

        return messages;
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
            String jsonData = aiService.buildOpenAIChunkJson(openAIMessageId, created, model, content);
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
                    String jsonData = aiService.buildOpenAIChunkJson(openAIMessageId, created, model, chunk);
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
                String jsonData = aiService.buildOpenAIChunkJson(openAIMessageId, created, model, content);
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
                String jsonData = aiService.buildOpenAIChunkJson(openAIMessageId, created, model, chunk);
                events.add(ServerSentEvent.<String>builder()
                        .data(jsonData)
                        .event("chunk")
                        .build());
            }
        } else {
            // 如果没有chunks，直接推送完整内容
            String jsonData = aiService.buildOpenAIChunkJson(openAIMessageId, created, model, existingContent);
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
     * 获取缓存的历史消息列表
     * 
     * @param conversationId 会话ID
     * @param limit          需要获取的消息数量
     * @return 历史消息列表，如果缓存未命中返回null
     */
    private List<Messages> getCachedHistoryMessages(Long conversationId, int limit) {
        if (conversationId == null) {
            return null;
        }

        String cacheKey = RedisConstants.CONVERSATION_MESSAGES_KEY + conversationId;
        try {
            TypeReference<List<Messages>> typeRef = new TypeReference<List<Messages>>() {
            };
            List<Messages> cachedMessages = redisCacheUtil.get(cacheKey, typeRef);
            if (cachedMessages != null && !cachedMessages.isEmpty()) {
                log.debug("从缓存获取历史消息: conversationId={}, count={}", conversationId, cachedMessages.size());
                // 如果缓存的消息数量足够，直接返回
                if (cachedMessages.size() >= limit) {
                    // 返回最近 limit 条消息
                    int startIndex = Math.max(0, cachedMessages.size() - limit);
                    return new ArrayList<>(cachedMessages.subList(startIndex, cachedMessages.size()));
                }
                // 如果缓存的消息数量不足，返回所有缓存的消息（后续会从数据库补充）
                return new ArrayList<>(cachedMessages);
            }
        } catch (Exception e) {
            log.warn("获取缓存历史消息失败: conversationId={}", conversationId, e);
        }
        return null;
    }

    /**
     * 更新缓存的历史消息列表（追加新消息）
     * 
     * @param conversationId 会话ID
     * @param newMessages    新消息列表（会追加到缓存末尾）
     */
    private void updateCachedHistoryMessages(Long conversationId, List<Messages> newMessages) {
        if (conversationId == null || newMessages == null || newMessages.isEmpty()) {
            return;
        }

        String cacheKey = RedisConstants.CONVERSATION_MESSAGES_KEY + conversationId;
        try {
            // 获取现有缓存
            TypeReference<List<Messages>> typeRef = new TypeReference<List<Messages>>() {
            };
            List<Messages> existingMessages = redisCacheUtil.get(cacheKey, typeRef);

            List<Messages> updatedMessages;
            if (existingMessages != null && !existingMessages.isEmpty()) {
                // 追加新消息到现有列表
                updatedMessages = new ArrayList<>(existingMessages);
                updatedMessages.addAll(newMessages);
            } else {
                // 如果缓存不存在，直接使用新消息列表
                updatedMessages = new ArrayList<>(newMessages);
            }

            // 限制缓存大小：只保留最近 20 条消息
            int maxCacheSize = 20;
            if (updatedMessages.size() > maxCacheSize) {
                int startIndex = updatedMessages.size() - maxCacheSize;
                updatedMessages = new ArrayList<>(updatedMessages.subList(startIndex, updatedMessages.size()));
            }

            // 更新缓存
            redisCacheUtil.set(cacheKey, updatedMessages, RedisConstants.CONVERSATION_MESSAGES_TTL);
            log.debug("更新缓存历史消息: conversationId={}, totalCount={}", conversationId, updatedMessages.size());
        } catch (Exception e) {
            log.warn("更新缓存历史消息失败: conversationId={}", conversationId, e);
            // 缓存更新失败不影响主业务流程
        }
    }

    /**
     * 清除会话消息缓存
     * 
     * @param conversationId 会话ID
     */
    private void clearConversationMessagesCache(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        String cacheKey = RedisConstants.CONVERSATION_MESSAGES_KEY + conversationId;
        redisCacheUtil.delete(cacheKey);
        log.debug("已清除会话消息缓存: conversationId={}", conversationId);
    }

    /**
     * 清除会话缓存（包括会话信息和消息缓存）
     * 用于需要同时清除所有缓存的情况（如消息删除/编辑）
     * 
     * @param conversationId 会话ID
     */
    @SuppressWarnings("unused")
    private void clearConversationCache(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        String cacheKey = RedisConstants.CONVERSATION_KEY + conversationId;
        redisCacheUtil.delete(cacheKey);
        // 同时清除空值标记
        redisCacheUtil.deleteNullValue(cacheKey);
        // 清除消息缓存
        clearConversationMessagesCache(conversationId);
        log.debug("已清除会话缓存: conversationId={}", conversationId);
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
