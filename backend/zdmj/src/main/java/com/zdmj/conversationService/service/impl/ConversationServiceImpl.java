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
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.conversationService.support.ConversationContextSupport;
import com.zdmj.resumeService.service.ResumeService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话 Service 实现骨架
 */
@RequiredArgsConstructor
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final RedisUtil redisUtil;
    private final ResumeService resumeService;

    @Override
    public boolean updateById(Conversation entity) {
        boolean updated = super.updateById(entity);
        if (!updated || entity == null || entity.getId() == null) {
            return updated;
        }
        // 保证 conversation:{id} 缓存与数据库一致，避免 messageCount 等字段读到旧值
        redisUtil.set(RedisConstants.CONVERSATION_KEY + entity.getId(), entity, RedisConstants.CONVERSATION_TTL);
        return updated;
    }

    @Override
    public Conversation create(ConversationDTO conversationDTO) {
        Long userId = UserHolder.requireUserId();

        Conversation conversation = new Conversation();
        if (conversationDTO != null) {
            Map<String, Object> config = conversationDTO.getConfig() == null
                    ? new HashMap<>()
                    : new HashMap<>(conversationDTO.getConfig());
            config.putIfAbsent(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, false);
            conversation.setConfig(config);

            List<Map<String, Object>> contextList = conversationDTO.getContext() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(conversationDTO.getContext());
            boolean hasResume = contextList.stream()
                    .anyMatch(item -> item != null
                            && ConversationContextSupport.CONTEXT_TYPE_RESUME.equals(String.valueOf(item.get("type"))));
            if (!hasResume) {
                ConversationContextSupport.buildResumeContextEntry(resumeService.getMyResumeContent())
                        .ifPresent(contextList::add);
            }
            conversation.setContext(contextList.isEmpty() ? null : contextList);
        } else {
            Map<String, Object> config = new HashMap<>();
            config.put(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, false);
            conversation.setConfig(config);
            List<Map<String, Object>> contextList = new ArrayList<>();
            ConversationContextSupport.buildResumeContextEntry(resumeService.getMyResumeContent())
                    .ifPresent(contextList::add);
            conversation.setContext(contextList.isEmpty() ? null : contextList);
        }
        conversation.setUserId(userId);
        conversation.setMessageCount(0);

        boolean saved = save(conversation);
        if (!saved) {
            throw new BusinessException(ErrorCode.CONVERSATION_CREATE_FAILED);
        }
        return conversation;
    }

    @Override
    public Conversation getById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话ID不能为空");
        }
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话标题不能为空");
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
    public Conversation updateConfig(Long id, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话配置不能为空");
        }
        Long userId = UserHolder.requireUserId();
        Conversation conversation = getById(id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Map<String, Object> merged = conversation.getConfig() == null
                ? new HashMap<>()
                : new HashMap<>(conversation.getConfig());
        merged.putAll(config);
        conversation.setConfig(merged);

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
