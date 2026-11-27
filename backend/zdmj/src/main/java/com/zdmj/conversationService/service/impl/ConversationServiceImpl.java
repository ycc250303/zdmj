package com.zdmj.conversationService.service.impl;

import com.zdmj.conversationService.dto.ConversationDeletedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import java.time.Clock;
import java.time.LocalDateTime;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.ConversationService;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话维护。检索范围写入 config；删除事务同时清理会话与消息。
 */
@RequiredArgsConstructor
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * 创建会话。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 可为空；为空时系统知识库开关为关闭，不限定文档
     * @return 新会话
     * @throws BusinessException 检索配置非法或插入没有写入记录
     */
    @Override
    public ConversationResponse create(CurrentActor actor, ConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setConfig(prepareConfig(request == null ? new ConversationRequest(null) : request));
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
     * 按用户归属分页查询会话，按更新时间和编号倒序，默认每页20条、上限100条。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param page 页码，缺省或非正数时为1
     * @param limit 每页数量，缺省或非正数时为20，最多100
     * @return 当前页会话及分页统计
     */
    @Override
    public PageDTO<ConversationResponse> getByPage(CurrentActor actor, Integer page, Integer limit) {
        var result = conversationMapper.selectPage(PageRequests.toPage(PageRequests.normalize(page, limit)),
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getUserId, actor.userId())
                        .orderByDesc(Conversation::getUpdatedAt).orderByDesc(Conversation::getId));
        return PageDTO.from(result, result.getRecords().stream().map(this::convertToResponse).toList());
    }

    /**
     * 更新会话标题。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @param title 新标题
     * @return 更新后的会话
     * @throws BusinessException 标题为空或超过255个Unicode码点、会话不存在或无权访问
     */
    @Override
    public ConversationResponse updateTitle(CurrentActor actor, Long id, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话标题不能为空");
        }
        String trimmedTitle = title.trim();
        if (trimmedTitle.codePointCount(0, trimmedTitle.length()) > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话标题最多允许255个字符");
        }
        requireOwned(actor, id);
        int rows = conversationMapper.updateTitleByIdAndUserId(id, actor.userId(), trimmedTitle, LocalDateTime.now(clock));
        if (rows != 1) {
            throw new BusinessException(ErrorCode.CONVERSATION_UPDATE_FAILED);
        }
        return convertToResponse(requireById(id));
    }

    /**
     * 在短事务内锁定会话，校验并合并局部配置，保存后读取响应。
     * 与消息准备及删除共用会话行锁，避免并发请求覆盖其他字段。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 会话编号
     * @param config 局部配置；省略字段保留原值，明确 null 恢复该字段缺省含义
     * @return 更新后的会话
     * @throws BusinessException 配置为空或非法、会话不存在、无权访问或已有消息
     * @throws IllegalStateException 数据库中已存检索配置非法，或锁定后更新行数异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationResponse updateConfig(CurrentActor actor, Long id, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话配置不能为空");
        }
        requireOwned(actor, id);
        Conversation conversation = conversationMapper.selectOwnedForUpdate(id, actor.userId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (conversation.getMessageCount() != null && conversation.getMessageCount() > 0) {
            throw new BusinessException(ErrorCode.CONVERSATION_CONFIG_LOCKED);
        }

        try {
            ConversationRequest stored = new ConversationRequest(conversation.getConfig());
            stored.useSystemKnowledge();
            stored.ragDocumentIds();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("会话检索配置非法，conversationId=" + id, exception);
        }

        Map<String, Object> merged = conversation.getConfig() == null
                ? new HashMap<>()
                : new HashMap<>(conversation.getConfig());
        merged.putAll(config);

        Map<String, Object> sanitized = prepareConfig(new ConversationRequest(merged));

        int rows = conversationMapper.updateConfigByIdAndUserId(id, actor.userId(), sanitized, LocalDateTime.now(clock));
        if (rows != 1) {
            throw new IllegalStateException("锁定会话后配置必须更新一条记录，conversationId=" + id + "，rows=" + rows);
        }
        return convertToResponse(requireById(id));
    }

    /**
     * 组装待保存的配置，校验字段并丢弃未知键。
     *
     * @param request 原始或合并后的配置；字段缺省或为 null 时使用各字段缺省含义
     * @return 新配置，包含布尔开关，省略 null 文档范围并保留空列表
     * @throws BusinessException 请求字段类型或编号取值非法
     */
    private Map<String, Object> prepareConfig(ConversationRequest request) {
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("useSystemKnowledge", request.useSystemKnowledge());
            List<Long> ids = request.ragDocumentIds();
            if (ids != null) {
                config.put("ragDocumentIds", ids);
            }
            return config;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage(), exception);
        }
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
     * 提交成功后取消活动生成；删除回滚时生成继续。
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
        events.publishEvent(new ConversationDeletedEvent(actor, id));
    }

    private ConversationResponse convertToResponse(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getUserId(),
                conversation.getTitle(), conversation.getConfig(), conversation.getMessageCount(),
                conversation.getLastMessageAt());
    }
}
