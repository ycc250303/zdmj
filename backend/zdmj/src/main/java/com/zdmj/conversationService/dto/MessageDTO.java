package com.zdmj.conversationService.dto;

import com.zdmj.common.ai.LlmInputLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageDTO {
    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 消息
     */
    @NotBlank(message = "消息不能为空")
    @Size(max = LlmInputLimits.CHAT_MESSAGE_MAX_CHARS, message = "消息长度不能超过4000个字符")
    private String message;
}
