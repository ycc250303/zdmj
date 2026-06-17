package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 会话 Mapper 骨架
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 按用户查询会话列表
     */
    List<Conversation> selectByUserId(@Param("userId") Long userId);

    /**
     * 原子递增消息计数并返回最新 message_count。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID（权限隔离）
     * @param delta          递增值（本场景固定为2）
     * @return 更新后的 message_count；如果会话不存在或无权限，返回 null
     */
    Integer incrementMessageCountAndGet(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("delta") int delta);

    /**
     * 仅更新会话标题，避免回写旧的 message_count。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID（权限隔离）
     * @param title          会话标题
     * @return 影响行数
     */
    int updateTitleByIdAndUserId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("title") String title);

    /**
     * 仅更新会话 config，避免回写 Redis 缓存中过期的 message_count / last_message_at。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID（权限隔离）
     * @param config         合并后的完整 config
     * @return 影响行数
     */
    int updateConfigByIdAndUserId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("config") Map<String, Object> config);
}
