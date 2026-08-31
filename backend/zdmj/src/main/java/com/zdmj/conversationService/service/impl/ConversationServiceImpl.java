package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
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
import com.zdmj.resumeService.service.ResumeService;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final ResumeService resumeService;

    @Override
    public ConversationResponse create(ConversationRequest request) {
        Long userId = UserHolder.requireUserId();

        Conversation conversation = new Conversation();
        if (request != null) {
            Map<String, Object> config = request.getConfig() == null
                    ? new HashMap<>()
                    : new HashMap<>(request.getConfig());
            config.putIfAbsent(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, false);
            conversation.setConfig(config);

            List<Map<String, Object>> contextList = request.getContext() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(request.getContext());
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
        return convertToResponse(conversation);
    }

    @Override
    public ConversationResponse getById(Long id) {
        return convertToResponse(requireOwned(id));
    }

    @Override
    public Conversation requireOwned(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话ID不能为空");
        }
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        } else if (!conversation.getUserId().equals(UserHolder.requireUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return conversation;
    }

    @Override
    public List<ConversationResponse> getByUserId() {
        Long userId = UserHolder.requireUserId();
        List<Conversation> conversations = conversationMapper.selectByUserId(userId);
        return conversations.stream().map(this::convertToResponse).toList();
    }

    @Override
    public ConversationResponse updateTitle(Long id, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话标题不能为空");
        }
        Long userId = UserHolder.requireUserId();
        Conversation conversation = requireOwned(id);
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
    public ConversationResponse updateConfig(Long id, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话配置不能为空");
        }
        Long userId = UserHolder.requireUserId();
        Conversation conversation = requireOwned(id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Map<String, Object> merged = conversation.getConfig() == null
                ? new HashMap<>()
                : new HashMap<>(conversation.getConfig());
        merged.putAll(config);

        int rows = conversationMapper.updateConfigByIdAndUserId(id, userId, merged);
        if (rows != 1) {
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
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();
        Conversation conversation = requireOwned(id);
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
