package com.zdmj.conversationService.dto;

import lombok.Data;

@Data
public class MessagesDTO {
    /**
     * 消息内容
     */
    private String content;

    /**
     * AI模型（可选，如果指定则覆盖会话的默认模型）
     */
    private String model;

    /**
     * 模型配置（可选，JSON格式字符串）
     * 示例：{"temperature": 0.7, "max_tokens": 2000}
     */
    private String config;
}
