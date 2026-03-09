package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Messages;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/**
 * 会话Mapper接口
 */

@Mapper
public interface MessagesMapper extends BaseMapper<Messages> {
    /**
     * 根据会话ID查询消息记录
     * 
     * @param conversationId 会话ID
     * @param limit 每页数量
     * @param offset 起始偏移量
     * @return 消息记录
     */
    List<Messages> selectMessagesByConversationId(Long conversationId,Integer limit,Integer offset);
}