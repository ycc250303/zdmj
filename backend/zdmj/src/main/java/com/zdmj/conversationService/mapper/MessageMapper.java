package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息 Mapper 骨架
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 按会话查询消息列表
     * 
     * @param conversationId 会话ID
     * @param offset         偏移量
     * @param limit          每页条数
     * @return 消息列表
     */
    List<Message> selectPageByConversationId(@Param("conversationId") Long conversationId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 按会话查询消息数量
     * 
     * @param conversationId 会话ID
     * @return 消息数量
     */
    Integer selectMessageCountByConversationId(@Param("conversationId") Long conversationId);
}
