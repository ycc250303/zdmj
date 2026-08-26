package com.zdmj.conversationService.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建会话请求
 */
@Data
public class ConversationRequest {
    /**
     * 对话ID
     */
    private Long id;

    /**
     * 对话配置（JSONB对象）
     */
    private Map<String, Object> config;

    /**
     * 上下文信息（JSONB数组）
     */
    private List<Map<String, Object>> context;
}
