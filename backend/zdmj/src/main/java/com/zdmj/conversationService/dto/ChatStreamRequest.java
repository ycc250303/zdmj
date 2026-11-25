package com.zdmj.conversationService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 流式对话请求体。检索范围只读会话 config，不接受逐条覆盖。
 *
 * @param conversationId 会话ID
 * @param message        消息
 */
public record ChatStreamRequest(
                @NotNull(message = "会话ID不能为空") Long conversationId,

                @NotBlank(message = "消息不能为空") @Size(max = 4000, message = "消息长度不能超过4000个字符") String message) {
}
