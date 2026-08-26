package com.zdmj.conversationService.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话响应（镜像当前 Conversation Entity JSON）
 */
@Data
public class ConversationResponse {

    private Long id;

    private Long userId;

    private String title;

    private Map<String, Object> config;

    private List<Map<String, Object>> context;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;
}
