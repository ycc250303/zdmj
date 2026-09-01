package com.zdmj.conversationService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流式对话请求体。检索范围只读会话 config，不接受逐条覆盖。
 */
@Data
public class ChatStreamRequest {
    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 消息
     */
    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息长度不能超过4000个字符")
    private String message;
}
