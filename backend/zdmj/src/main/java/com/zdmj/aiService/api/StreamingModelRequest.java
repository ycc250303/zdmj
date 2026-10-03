package com.zdmj.aiService.api;

import java.util.Map;

/**
 * 带会话标识的流式生成请求。是否绑定会话记忆由调用方传入的会话标识决定。
 *
 * @param conversationId 会话主键
 * @param message        用户消息
 * @param promptName     classpath 提示词名称，空则不附加系统提示词
 * @param promptVars     提示词占位符，空则不替换
 */
public record StreamingModelRequest(
        Long conversationId,
        String message,
        String promptName,
        Map<String, Object> promptVars) {
}
