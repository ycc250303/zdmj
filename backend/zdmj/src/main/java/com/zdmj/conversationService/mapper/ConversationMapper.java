package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Conversations;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/**
 * 会话Mapper接口
 */

@Mapper
public interface ConversationMapper extends BaseMapper<Conversations> {
    /**
     * 根据用户ID查询会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    List<Conversations> selectConversationsByUserId(Long userId);
}