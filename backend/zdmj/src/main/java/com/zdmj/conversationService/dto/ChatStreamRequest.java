package com.zdmj.conversationService.dto;

import lombok.Data;

/**
 * 流式对话请求体
 */
@Data
public class ChatStreamRequest {

    /** 会话 ID */
    private Long conversationId;

    /** 用户消息内容 */
    private String message;
}
