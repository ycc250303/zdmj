package com.zdmj.conversationService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;

import java.util.List;
import java.util.Map;

/**
 * 会话 Service
 */
public interface ConversationService {

    /**
     * 创建会话
     *
     * @param actor 已认证操作者，包含用户编号
     */
    ConversationResponse create(CurrentActor actor, ConversationRequest request);

    /**
     * 根据ID查询会话（HTTP 出参）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    ConversationResponse getById(CurrentActor actor, Long id);

    /**
     * 鉴权加载会话实体（供本域与 MessageService 使用）
     *
     * @param actor 已认证操作者，包含用户编号
     */
    Conversation requireOwned(CurrentActor actor, Long id);

    /**
     * 查询当前用户会话列表
     *
     * @param actor 已认证操作者，包含用户编号
     */
    List<ConversationResponse> getByUserId(CurrentActor actor);

    /**
     * 修改会话标题
     *
     * @param actor 已认证操作者，包含用户编号
     */
    ConversationResponse updateTitle(CurrentActor actor, Long id, String title);

    /**
     * 更新检索配置。仅在尚未发出首条消息时允许（message_count = 0）。
     *
     * @param actor 已认证操作者，包含用户编号
     */
    ConversationResponse updateConfig(CurrentActor actor, Long id, Map<String, Object> config);

    /**
     * 删除会话
     *
     * @param actor 已认证操作者，包含用户编号
     */
    void delete(CurrentActor actor, Long id);
}
