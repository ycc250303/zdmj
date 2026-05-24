package com.zdmj.conversationService.dto;

import lombok.Data;

/**
 * 流式对话续传请求体
 */
@Data
public class ChatResumeRequest {

    /** 流式消息 ID，建议等于 assistantMessageId */
    private Long streamId;

    /** 前端已接收字符数 */
    private Integer offset;
}
