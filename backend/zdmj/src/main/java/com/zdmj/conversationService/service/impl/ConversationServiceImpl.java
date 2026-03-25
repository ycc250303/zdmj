package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.cache.RedisConstants;
import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ConversationDTO;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.ConversationStructMapper;
import com.zdmj.conversationService.service.ConversationService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话 Service 实现骨架
 */
@RequiredArgsConstructor
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationStructMapper conversationStructMapper;
    private final RedisUtil redisUtil;

    @Override
    public Conversation create(ConversationDTO conversationDTO) {
        Long userId = UserHolder.requireUserId();

        Conversation conversation = new Conversation();
        conversationStructMapper.updateEntityFromDto(conversationDTO, conversation);
        conversation.setUserId(userId);

        boolean saved = save(conversation);
        if (!saved) {
            throw new BusinessException(ErrorCode.CONVERSATION_CREATE_FAILED);
        }
        return conversation;
    }

    @Override
    public Conversation getById(Long id) {
        String key = RedisConstants.CONVERSATION_KEY + id;
        Conversation conversation = redisUtil.get(key, Conversation.class);
        if (conversation != null) {
            if (!conversation.getUserId().equals(UserHolder.requireUserId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
            return conversation;
        }
        conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        } else if (!conversation.getUserId().equals(UserHolder.requireUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        redisUtil.set(key, conversation, RedisConstants.CONVERSATION_TTL);
        return conversation;
    }

    @Override
    public List<Conversation> getByUserId() {
        Long userId = UserHolder.requireUserId();
        List<Conversation> conversations = conversationMapper.selectByUserId(userId);
        return conversations;
    }

    @Override
    public Conversation updateTitle(Long id, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), "会话标题不能为空");
        }
        Long userId = UserHolder.requireUserId();
        Conversation conversation = getById(id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        conversation.setTitle(title.trim());
        boolean updated = updateById(conversation);
        if (!updated) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED);
        }
        redisUtil.set(RedisConstants.CONVERSATION_KEY + id, conversation, RedisConstants.CONVERSATION_TTL);
        return conversation;
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        Conversation conversation = getById(id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        boolean deleted = removeById(id);
        if (!deleted) {
            throw new BusinessException(ErrorCode.CONVERSATION_DELETE_FAILED);
        }
        redisUtil.delete(RedisConstants.CONVERSATION_KEY + id);
    }
}
