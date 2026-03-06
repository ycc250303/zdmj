package com.zdmj.conversationService.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.conversationService.dto.ConversationsDTO;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.exception.BusinessException;
import com.zdmj.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 会话服务实现类
 */
@Slf4j
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversations>
        implements ConversationService {

    @Override
    public Conversations create(ConversationsDTO conversationDTO) {
        Long userId = UserHolder.requireUserId();

        Conversations conversation = new Conversations();
        conversation.setUserId(userId);
        conversation.setProjectId(conversationDTO.getProjectId());
        conversation.setTitle("新会话");
        conversation.setModel(conversationDTO.getModel() != null ? conversationDTO.getModel() : "qwen");
        conversation.setConfig(conversationDTO.getConfig());
        conversation.setMessageCount(0);
        conversation.setCreatedAt(DateTimeUtil.now());
        conversation.setUpdatedAt(DateTimeUtil.now());

        boolean saved = save(conversation);
        if (!saved) {
            throw new BusinessException(ErrorCode.CONVERSATION_CREATE_FAILED);
        }
        return conversation;
    }

    @Override
    public List<Conversations> getByUserId() {
        Long userId = UserHolder.requireUserId();
        return baseMapper.selectConversationsByUserId(userId);
    }

    @Override
    public Conversations getById(Long id) {
        Long userId = UserHolder.requireUserId();

        Conversations conversation = baseMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        return conversation;
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.requireUserId();

        Conversations conversation = getById(id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        boolean deleted = removeById(id);
        if (!deleted) {
            throw new BusinessException(ErrorCode.CONVERSATION_DELETE_FAILED);
        }
    }

    @Override
    public void pin(Long id) {
        // TODO: 待实现 - 会话置顶功能
        throw new BusinessException(ErrorCode.FEATURE_NOT_IMPLEMENTED);
    }

    @Override
    public void rename(Long id, String title) {
        // TODO: 待实现 - 会话重命名功能
        // 需要实现：
        // 1. 验证会话是否存在且属于当前用户
        // 2. 验证标题不为空且长度合理（建议不超过50个字符）
        // 3. 更新会话标题
        // 4. 更新updatedAt时间戳
        throw new BusinessException(ErrorCode.FEATURE_NOT_IMPLEMENTED);
    }
}
