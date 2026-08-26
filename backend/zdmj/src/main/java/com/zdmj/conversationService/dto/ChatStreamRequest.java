package com.zdmj.conversationService.dto;

import com.zdmj.common.ai.LlmInputLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 流式对话请求体
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
    @Size(max = LlmInputLimits.CHAT_MESSAGE_MAX_CHARS, message = "消息长度不能超过4000个字符")
    private String message;

    /**
     * 参与 RAG 检索的知识文档 ID 列表。
     * null 表示沿用会话 config；空列表表示本次不检索用户私有文档。
     */
    private List<Long> ragDocumentIds;

    /**
     * 是否检索系统知识库（scope=2）。
     * null 表示沿用会话 config.useSystemKnowledge。
     */
    private Boolean useSystemKnowledge;
}
