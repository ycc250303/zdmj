package com.zdmj.conversationService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;

import java.util.List;
import java.util.Map;

/**
 * 会话 Service
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 创建会话
     */
    ConversationResponse create(ConversationRequest request);

    /**
     * 根据ID查询会话（HTTP 出参）
     */
    ConversationResponse getById(Long id);

    /**
     * 鉴权加载会话实体（供本域与 MessageService 使用）
     */
    Conversation requireOwned(Long id);

    /**
     * 查询当前用户会话列表
     */
    List<ConversationResponse> getByUserId();

    /**
     * 修改会话标题
     */
    ConversationResponse updateTitle(Long id, String title);

    /**
     * 更新检索配置。仅在尚未发出首条消息时允许（message_count = 0）。
     */
    ConversationResponse updateConfig(Long id, Map<String, Object> config);

    /**
     * 删除会话
     */
    void delete(Long id);
}
