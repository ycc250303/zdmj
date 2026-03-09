package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Messages;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话Mapper接口
 */

@Mapper
public interface MessagesMapper extends BaseMapper<Messages> {
    /**
     * 根据会话ID查询消息记录
     * 
     * @param conversationId 会话ID
     * @param limit          每页数量
     * @param offset         起始偏移量
     * @return 消息记录
     */
    List<Messages> selectMessagesByConversationId(Long conversationId, Integer limit, Integer offset);

    /**
     * 删除指定 id 之后的全部消息
     * 
     * @param conversationId 会话ID
     * @param messageId      消息ID
     * @return 删除的消息数量
     */
    Integer deleteMessagesByIdAfter(@Param("conversationId") Long conversationId, @Param("messageId") Long messageId);

    /**
     * 查询会话的最后一条消息的创建时间
     * 
     * @param conversationId 会话ID
     * @return 最后一条消息的创建时间，如果没有消息则返回null
     */
    LocalDateTime selectLastMessageCreatedAt(@Param("conversationId") Long conversationId);
}