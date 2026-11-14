package com.zdmj.conversationService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话 Service 实现
 */
@RequiredArgsConstructor
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatMemory chatMemory;

    @Override
    public ConversationResponse create(CurrentActor actor, ConversationRequest request) {
        Long userId = actor.userId();

        Conversation conversation = new Conversation();
        conversation.setConfig(ConversationContextSupport.freezeConfig(request == null ? null : request.getConfig()));
        List<Map<String, Object>> context = request == null ? null : request.getContext();
        conversation.setContext(context == null || context.isEmpty() ? null : context);
        conversation.setUserId(userId);
        conversation.setMessageCount(0);

        boolean saved = save(conversation);
        if (!saved) {
            throw new BusinessException(ErrorCode.CONVERSATION_CREATE_FAILED);
        }
        return convertToResponse(conversation);
    }

    @Override
    public ConversationResponse getById(CurrentActor actor, Long id) {
        return convertToResponse(requireOwned(actor, id));
    }

    @Override
    public Conversation requireOwned(CurrentActor actor, Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话ID不能为空");
        }
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        } else if (!conversation.getUserId().equals(actor.userId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return conversation;
    }

    @Override
    public List<ConversationResponse> getByUserId(CurrentActor actor) {
        Long userId = actor.userId();
        List<Conversation> conversations = conversationMapper.selectByUserId(userId);
        return conversations.stream().map(this::convertToResponse).toList();
    }

    @Override
    public ConversationResponse updateTitle(CurrentActor actor, Long id, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话标题不能为空");
        }
        Long userId = actor.userId();
        Conversation conversation = requireOwned(actor, id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        String trimmedTitle = title.trim();
        int rows = conversationMapper.updateTitleByIdAndUserId(id, userId, trimmedTitle);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED);
        }
        return convertToResponse(requireById(id));
    }

    @Override
    public ConversationResponse updateConfig(CurrentActor actor, Long id, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话配置不能为空");
        }
        Long userId = actor.userId();
        Conversation conversation = requireOwned(actor, id);
        if (conversation.getMessageCount() != null && conversation.getMessageCount() > 0) {
            throw new BusinessException(ErrorCode.CONVERSATION_CONFIG_LOCKED);
        }

        Map<String, Object> merged = conversation.getConfig() == null
                ? new HashMap<>()
                : new HashMap<>(conversation.getConfig());
        merged.putAll(config);
        Map<String, Object> sanitized = ConversationContextSupport.freezeConfig(merged);

        int rows = conversationMapper.updateConfigByIdAndUserId(id, userId, sanitized);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(CurrentActor actor, Long id) {
        Long userId = actor.userId();
        Conversation conversation = requireOwned(actor, id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, id));
        chatMemory.clear(String.valueOf(id));
        boolean deleted = removeById(id);
        if (!deleted) {
            throw new BusinessException(ErrorCode.CONVERSATION_DELETE_FAILED);
        }
    }

    private ConversationResponse convertToResponse(Conversation conversation) {
        ConversationResponse response = new ConversationResponse();
        BeanUtils.copyProperties(conversation, response);
        return response;
    }
}
