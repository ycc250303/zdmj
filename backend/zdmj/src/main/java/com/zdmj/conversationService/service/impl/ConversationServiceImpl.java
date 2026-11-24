package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.conversationService.support.ConversationContextSupport;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话维护。检索范围写入 config；删除事务同时清理会话与消息。
 */
@RequiredArgsConstructor
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    /**
     * 创建会话。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 可为空；为空时系统知识库开关为关闭，不限定文档
     * @return 新会话
     * @throws BusinessException 插入没有写入记录
     */
    @Override
    public ConversationResponse create(CurrentActor actor, ConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setConfig(ConversationContextSupport.freezeConfig(request == null ? null : request.config()));
        conversation.setUserId(actor.userId());
        conversation.setMessageCount(0);
        if (conversationMapper.insert(conversation) != 1) {
            throw new BusinessException(ErrorCode.CONVERSATION_CREATE_FAILED);
        }
        if (conversation.getId() == null) {
            throw new IllegalStateException("会话创建后必须回填编号");
        }
        return convertToResponse(conversation);
    }

    /**
     * 获取会话。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @return 会话
     * @throws BusinessException 编号为空、会话不存在或无权访问
     */
    @Override
    public ConversationResponse getById(CurrentActor actor, Long id) {
        return convertToResponse(requireOwned(actor, id));
    }

    /**
     * 确认会话存在且属于当前用户。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @return 归属当前用户的会话
     * @throws BusinessException 编号为空、会话不存在或无权访问
     */
    public Conversation requireOwned(CurrentActor actor, Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话ID不能为空");
        }
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!conversation.getUserId().equals(actor.userId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return conversation;
    }

    /**
     * 获取用户会话。
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 用户会话
     */
    @Override
    public List<ConversationResponse> getByUserId(CurrentActor actor) {
        return conversationMapper.selectByUserId(actor.userId()).stream().map(this::convertToResponse).toList();
    }

    /**
     * 更新会话标题。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @param title 新标题
     * @return 更新后的会话
     * @throws BusinessException 标题为空、会话不存在或无权访问
     */
    @Override
    public ConversationResponse updateTitle(CurrentActor actor, Long id, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话标题不能为空");
        }
        requireOwned(actor, id);
        String trimmedTitle = title.trim();
        int rows = conversationMapper.updateTitleByIdAndUserId(id, actor.userId(), trimmedTitle);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED);
        }
        return convertToResponse(requireById(id));
    }

    /**
     * 更新会话配置。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @param config 新配置
     * @return 更新后的会话
     * @throws BusinessException 配置为空、会话不存在或无权访问
     */
    @Override
    public ConversationResponse updateConfig(CurrentActor actor, Long id, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话配置不能为空");
        }
        Conversation conversation = requireOwned(actor, id);
        if (conversation.getMessageCount() != null && conversation.getMessageCount() > 0) {
            throw new BusinessException(ErrorCode.CONVERSATION_CONFIG_LOCKED);
        }

        Map<String, Object> merged = conversation.getConfig() == null
                ? new HashMap<>()
                : new HashMap<>(conversation.getConfig());
        merged.putAll(config);

        Map<String, Object> sanitized = ConversationContextSupport.freezeConfig(merged);

        int rows = conversationMapper.updateConfigByIdAndUserId(id, actor.userId(), sanitized);
        if (rows != 1) {
            Conversation latest = conversationMapper.selectById(id);
            if (latest != null && latest.getMessageCount() != null && latest.getMessageCount() > 0) {
                throw new BusinessException(ErrorCode.CONVERSATION_CONFIG_LOCKED);
            }
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED);
        }
        return convertToResponse(requireById(id));
    }

    /** 局部更新后从数据库重新加载，保证返回最新字段。 */
    private Conversation requireById(Long id) {
        Conversation refreshed = conversationMapper.selectById(id);
        if (refreshed == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return refreshed;
    }

    /**
     * 删除会话及该用户在此会话下的消息。
     * 先锁定会话，与消息准备及结束保存串行执行，两张表在同一事务中删除。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @throws BusinessException 会话不存在、无权访问，或会话行没有删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(CurrentActor actor, Long id) {
        requireOwned(actor, id);
        Long userId = actor.userId();
        if (conversationMapper.selectOwnedForUpdate(id, userId) == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        // 删除消息
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, id)
                .eq(Message::getUserId, userId));
        // 删除会话
        int deleted = conversationMapper.delete(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .eq(Conversation::getUserId, userId));
        if (deleted != 1) {
            throw new BusinessException(ErrorCode.CONVERSATION_DELETE_FAILED);
        }
    }

    private ConversationResponse convertToResponse(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getUserId(),
                conversation.getTitle(), conversation.getConfig(), conversation.getMessageCount(),
                conversation.getLastMessageAt());
    }
}
