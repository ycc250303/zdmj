package com.zdmj.conversationService.dto;

import java.util.Map;

/**
 * 创建会话请求。检索范围只接受 useSystemKnowledge、ragDocumentIds，其余键在保存时丢弃。
 *
 * @param config 对话配置。创建后、发出首条消息前可改。
 */
public record ConversationRequest(
        Map<String, Object> config) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public ConversationRequest {
        config = config == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(config));
    }
}
