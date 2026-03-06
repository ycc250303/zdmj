package com.zdmj.conversationService.service;

import java.util.List;

import com.zdmj.conversationService.dto.ConversationsDTO;
import com.zdmj.conversationService.entity.Conversations;

/**
 * 会话服务接口
 */
public interface ConversationService {

    /**
     * 创建会话
     * 
     * @param conversationDTO 会话DTO
     * @return 会话
     */
    Conversations create(ConversationsDTO conversationDTO);

    /**
     * 根据用户ID查询会话列表
     * 
     * @return 会话列表
     */
    List<Conversations> getByUserId();

    /**
     * 根据会话ID获取会话详情
     * 
     * @param id 会话ID
     * @return 会话
     */
    Conversations getById(Long id);

    /**
     * 删除会话
     * 
     * @param id 会话ID
     */
    void delete(Long id);

    /**
     * 置顶会话（待实现）
     * 
     * @param id 会话ID
     */
    void pin(Long id);

    /**
     * 重命名会话（待实现）
     * 
     * @param id    会话ID
     * @param title 新标题
     */
    void rename(Long id, String title);
}
