package com.zdmj.conversationService.dto;

import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.entity.Conversation;

/**
 * 消息准备事务提交的结果，只在本轮生成内部使用，实体由调用方管理。
 * @param assistantMsg 已登记的助手占位消息
 * @param conversation 已校验归属并读取配置的会话
 * @param request 携带历史消息与生成状态说明的模型输入
 */
public record PreparedConversationMessages(Message assistantMsg, Conversation conversation, ModelRequest request) {
}
