package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zdmj.conversationService.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息 Mapper 骨架
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 按会话分页查询消息（分页由 MyBatis-Plus 插件处理）。
     */
    IPage<Message> selectPageByConversationId(IPage<Message> page, @Param("conversationId") Long conversationId);
}
