package com.zdmj.conversationService.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话响应（镜像当前 Conversation Entity JSON）
 *
 * @param id 会话编号，对应 conversations.id
 * @param userId 所属用户编号
 * @param title 会话标题
 * @param config 会话检索配置，对应 conversations.config；仅复制映射结构
 * @param messageCount 消息数量
 * @param lastMessageAt 最后消息时间
 */
public record ConversationResponse(
        Long id,
        Long userId,
        String title,
        Map<String, Object> config,
        Integer messageCount,
        LocalDateTime lastMessageAt) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public ConversationResponse {
        config = config == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(config));
    }
}
