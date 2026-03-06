package com.zdmj.conversationService.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.conversationService.dto.MessagesDTO;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.entity.Messages;
import com.zdmj.conversationService.mapper.MessagesMapper;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.exception.BusinessException;
import com.zdmj.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息服务实现类
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessagesMapper, Messages> implements MessageService {

    private final ConversationMapper conversationMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public MessageServiceImpl(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Override
    @Transactional
    public Messages createMessage(MessagesDTO messagesDTO, Long conversationId) {
        Long userId = UserHolder.requireUserId();

        // 验证会话是否存在且属于当前用户
        Conversations conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 验证消息内容
        if (messagesDTO.getContent() == null || messagesDTO.getContent().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 确定使用的模型（优先使用消息指定的模型，否则使用会话的默认模型）
        String model = messagesDTO.getModel() != null ? messagesDTO.getModel() : conversation.getModel();
        if (model == null || model.trim().isEmpty()) {
            model = "qwen"; // 默认模型
        }

        // 获取当前消息序号（会话中已有消息数 + 1）
        int currentSequence = conversation.getMessageCount() + 1;

        // 1. 创建并保存用户消息
        Messages userMessage = createUserMessage(conversationId, userId, messagesDTO.getContent(), currentSequence,
                model);
        boolean userMessageSaved = save(userMessage);
        if (!userMessageSaved) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 2. 获取历史消息（用于构建对话上下文）
        List<Messages> historyMessages = getMessagesByConversationId(conversationId, 1, 20);

        // 3. 调用LLM生成AI回复
        // TODO: 调用LLM服务生成AI回复
        // 需要实现：
        // - 构建对话上下文（历史消息 + 当前用户消息）
        // - 如果会话关联了项目，需要注入项目上下文（RAG）
        // - 如果会话关联了知识库，需要注入知识库上下文（RAG）
        // - 调用ChatService.chat()或chatStream()方法
        // - 处理流式/非流式响应
        String aiResponse = generateAIResponse(conversation, historyMessages, messagesDTO.getContent(), model,
                messagesDTO.getConfig());

        // 4. 创建并保存AI回复消息
        Messages aiMessage = createAssistantMessage(conversationId, userId, aiResponse, currentSequence + 1, model);
        boolean aiMessageSaved = save(aiMessage);
        if (!aiMessageSaved) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 5. 如果是第一条消息，生成会话标题
        boolean isFirstMessage = conversation.getMessageCount() == 0;
        if (isFirstMessage) {
            // TODO: 调用LLM根据第一条消息内容生成会话标题
            // 需要实现：
            // - 调用ChatService生成标题（可以使用简化的prompt）
            // - 标题应该简洁（建议不超过30个字符）
            String generatedTitle = generateConversationTitle(messagesDTO.getContent());
            if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                conversation.setTitle(generatedTitle);
            }
        }

        // 6. 更新会话统计信息
        conversation.setMessageCount(conversation.getMessageCount() + 2); // 用户消息 + AI回复
        conversation.setLastMessageAt(DateTimeUtil.now());
        conversation.setUpdatedAt(DateTimeUtil.now());
        conversationMapper.updateById(conversation);

        return userMessage;
    }

    /**
     * 创建用户消息
     */
    private Messages createUserMessage(Long conversationId, Long userId, String content, int sequence, String model) {
        Messages message = new Messages();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(MessageRole.USER.getCode());
        message.setContent(content);
        message.setSequence(sequence);
        message.setMetadata(buildMessageMetadata(model, null, null));
        message.setCreatedAt(DateTimeUtil.now());
        message.setUpdatedAt(DateTimeUtil.now());
        return message;
    }

    /**
     * 创建AI助手消息
     */
    private Messages createAssistantMessage(Long conversationId, Long userId, String content, int sequence,
            String model) {
        Messages message = new Messages();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(MessageRole.ASSISTANT.getCode());
        message.setContent(content);
        message.setSequence(sequence);
        // TODO: 从LLM响应中提取token使用情况、生成时间等信息
        message.setMetadata(buildMessageMetadata(model, null, null));
        message.setCreatedAt(DateTimeUtil.now());
        message.setUpdatedAt(DateTimeUtil.now());
        return message;
    }

    /**
     * 构建消息元数据（JSONB格式）
     */
    private String buildMessageMetadata(String model, Map<String, Object> tokens, Long generationTimeMs) {
        try {
            Map<String, Object> metadata = new HashMap<>();

            // 模型信息
            if (model != null) {
                metadata.put("model", model);
            }

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
     * 生成AI回复
     * TODO: 实现LLM调用逻辑
     */
    private String generateAIResponse(Conversations conversation, List<Messages> historyMessages,
            String userMessage, String model, String config) {
        // TODO: 实现LLM调用
        // 1. 构建对话上下文
        // 2. 如果conversation.getProjectId()不为null，获取项目信息并注入上下文
        // 3. 如果conversation.getContext()包含知识库ID，获取知识库内容并注入上下文（RAG）
        // 4. 调用ChatService.chat()方法
        // 5. 返回AI回复内容

        // 临时返回占位文本
        return "AI回复功能待实现";
    }

    /**
     * 生成会话标题
     * TODO: 实现标题生成逻辑
     */
    private String generateConversationTitle(String firstMessage) {
        // TODO: 调用LLM生成标题
        // 1. 构建简化的prompt，要求根据第一条消息生成简洁的标题
        // 2. 调用ChatService.chat()方法
        // 3. 提取并返回标题（限制长度不超过30个字符）

        // 临时返回：截取前30个字符作为标题
        if (firstMessage != null && firstMessage.length() > 0) {
            String title = firstMessage.trim();
            if (title.length() > 30) {
                title = title.substring(0, 30) + "...";
            }
            return title;
        }
        return "新会话";
    }

    @Override
    public List<Messages> getMessagesByConversationId(Long conversationId, Integer page, Integer size) {
        Long userId = UserHolder.requireUserId();

        // 验证会话是否存在且属于当前用户
        Conversations conversation = conversationMapper.selectById(conversationId);
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
