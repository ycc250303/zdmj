package com.zdmj.conversationService.dto;

import lombok.Data;

@Data
public class MessageDTO {
    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 消息
     */
    private String message;
}
