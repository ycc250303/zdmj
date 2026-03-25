package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 Mapper 骨架
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 按用户查询会话列表
     */
    List<Conversation> selectByUserId(@Param("userId") Long userId);
}
