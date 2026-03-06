package com.zdmj.conversationService.service;

import java.util.List;

import com.zdmj.conversationService.dto.MessagesDTO;
import com.zdmj.conversationService.entity.Messages;

/**
 * 消息服务接口
 */
public interface MessageService {

    /**
     * 创建消息
     * 
     * @param messagesDTO    消息DTO
     * @param conversationId 会话ID
     * @return 消息
     */
    Messages createMessage(MessagesDTO messagesDTO, Long conversationId);

    /**
     * 根据会话ID查询消息列表（分页）
     * 
     * @param conversationId 会话ID
     * @param page           页码（从1开始）
     * @param size           每页数量
     * @return 消息列表
     */
    List<Messages> getMessagesByConversationId(Long conversationId, Integer page, Integer size);

    /**
     * 根据消息ID获取消息详情
     * 
     * @param messageId 消息ID
     * @return 消息
     */
    Messages getById(Long messageId);

    /**
     * 删除消息（待实现）
     * 
     * @param messageId 消息ID
     */
    void delete(Long messageId);

    /**
     * 编辑消息并重新发送（待实现）
     * 
     * @param messageId  消息ID
     * @param newContent 新的消息内容
     * @return 重新生成的消息
     */
    Messages editAndResend(Long messageId, String newContent);

    /**
     * 引用消息（待实现）
     * 
     * @param messageId      被引用的消息ID
     * @param conversationId 会话ID
     * @return 引用消息的ID
     */
    Long quote(Long messageId, Long conversationId);
}
