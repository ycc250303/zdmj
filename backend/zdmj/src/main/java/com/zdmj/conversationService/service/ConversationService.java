package com.zdmj.conversationService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.conversationService.dto.ConversationDTO;
import com.zdmj.conversationService.entity.Conversation;

import java.util.List;

/**
 * 会话 Service 骨架
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 创建会话
     * 
     * @param conversationDTO 会话DTO
     * @return 创建的会话
     */
    Conversation create(ConversationDTO conversationDTO);

    /**
     * 根据ID查询会话
     * 
     * @param id 会话ID
     * @return 查询的会话
     */
    Conversation getById(Long id);

    /**
     * 查询所有会话列表
     * 
     * @return 查询的会话列表
     */
    List<Conversation> getByUserId();

    /**
     * 修改会话标题
     * 
     * @param id    会话ID
     * @param title 新会话标题
     * @return 更新后的会话
     */
    Conversation updateTitle(Long id, String title);

    /**
     * 删除会话
     * 
     * @param id 会话ID
     */
    void delete(Long id);
}
