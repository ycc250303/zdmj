package com.zdmj.conversationService.dto;

import com.zdmj.common.context.CurrentActor;

/**
 * 会话删除结果，由事务提交后监听器处理活动生成。
 *
 * @param actor 已认证操作者，继续传递删除时校验的身份
 * @param conversationId 已删除会话编号
 */
public record ConversationDeletedEvent(CurrentActor actor, Long conversationId) { }
