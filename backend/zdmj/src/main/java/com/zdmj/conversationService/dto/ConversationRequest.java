package com.zdmj.conversationService.dto;

import lombok.Data;

import java.util.Map;

/**
 * 创建会话请求。检索范围只接受 useSystemKnowledge、ragDocumentIds，其余键在保存时丢弃。
 */
@Data
public class ConversationRequest {
    /**
     * 对话配置。创建后、发出首条消息前可改。
     */
    private Map<String, Object> config;
}
