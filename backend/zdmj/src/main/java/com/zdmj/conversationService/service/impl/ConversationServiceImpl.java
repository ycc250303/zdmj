package com.zdmj.conversationService.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.util.RedisCacheUtil;
import com.zdmj.common.util.RedisConstants;
import com.zdmj.conversationService.dto.ConversationsDTO;
import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.mapper.ProjectExperienceMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 会话服务实现类
 */
@Slf4j
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversations>
        implements ConversationService {

    private final ProjectExperienceMapper projectExperienceMapper;
    private final RedisCacheUtil redisCacheUtil;

    // 标题最大长度
    private static final int MAX_TITLE_LENGTH = 20;

    public ConversationServiceImpl(ProjectExperienceMapper projectExperienceMapper,
            RedisCacheUtil redisCacheUtil) {
        this.projectExperienceMapper = projectExperienceMapper;
        this.redisCacheUtil = redisCacheUtil;
    }

    @Override
    public Conversations create(ConversationsDTO conversationDTO) {
        Long userId = UserHolder.requireUserId();

        // 如果项目ID不为空，则验证项目是否存在且属于当前用户
        if (conversationDTO.getProjectId() != null) {
            // 验证项目是否存在且属于当前用户
            ProjectExperience project = projectExperienceMapper.selectById(conversationDTO.getProjectId());
            if (project == null) {
                throw new BusinessException(ErrorCode.PROJECT_EXPERIENCE_NOT_FOUND);
            }
            if (!project.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(), "无权访问他人项目");
            }
        }

        // 创建会话
        Conversations conversation = new Conversations();
        conversation.setUserId(userId);
        conversation.setProjectId(conversationDTO.getProjectId());
        conversation.setTitle("新会话");
        conversation.setModel("qwen"); // 使用默认模型
        conversation.setConfig("{}"); // 使用默认空配置
        conversation.setMessageCount(0);
        conversation.setCreatedAt(DateTimeUtil.now());
        conversation.setUpdatedAt(DateTimeUtil.now());

        boolean saved = save(conversation);
        if (!saved) {
            throw new BusinessException(ErrorCode.CONVERSATION_CREATE_FAILED);
        }

        log.info("创建会话成功: conversationId={}, projectId={}", conversation.getId(), conversationDTO.getProjectId());
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
    @Transactional
    public void rename(Long id, String title) {
        Long userId = UserHolder.requireUserId();

        // 验证会话是否存在且属于当前用户
        Conversations conversation = getById(id);
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 验证标题不为空
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED.getCode(), "会话标题不能为空");
        }

        // 验证标题长度（不超过20个字符）
        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED.getCode(),
                    String.format("会话标题长度不能超过%d个字符", MAX_TITLE_LENGTH));
        }

        // 使用LambdaUpdateWrapper只更新title和updatedAt字段，避免更新null字段
        LambdaUpdateWrapper<Conversations> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Conversations::getId, id)
                .eq(Conversations::getUserId, userId) // 确保只能更新自己的会话
                .set(Conversations::getTitle, trimmedTitle)
                .set(Conversations::getUpdatedAt, DateTimeUtil.now());

        // 更新数据库
        boolean updated = update(updateWrapper);
        if (!updated) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED);
        }

        // 清除缓存
        clearConversationCache(id);

        log.info("会话重命名成功: conversationId={}, newTitle={}", id, trimmedTitle);
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
}
