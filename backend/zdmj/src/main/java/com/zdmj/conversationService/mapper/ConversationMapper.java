package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.conversationService.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话 Mapper 骨架
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /** 在短事务中锁定归属用户的会话，供配置更新、消息准备、结束保存和删除共用。 */
    Conversation selectOwnedForUpdate(@Param("conversationId") Long conversationId,
            @Param("userId") Long userId);

    /**
     * 原子递增消息计数并返回最新 message_count。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID（权限隔离）
     * @param delta          递增值（本场景固定为2）
     * @param now 同一次写入的应用时间，用于最后消息时间和更新时间
     * @return 更新后的 message_count；如果会话不存在或无权限，返回 null
     */
    Integer incrementMessageCountAndGet(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("delta") int delta,
            @Param("now") LocalDateTime now);

    /**
     * 仅更新会话标题，避免回写旧的 message_count。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID（权限隔离）
     * @param title          会话标题
     * @param now 应用时钟生成的更新时间
     * @return 影响行数
     */
    int updateTitleByIdAndUserId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("title") String title,
            @Param("now") LocalDateTime now);

    /**
     * 仅在尚未发出首条消息时更新 config（WHERE message_count = 0）。
     *
     * @return 影响行数；已开始对话时为 0
     */
    int updateConfigByIdAndUserId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("config") Map<String, Object> config,
            @Param("now") LocalDateTime now);
}
