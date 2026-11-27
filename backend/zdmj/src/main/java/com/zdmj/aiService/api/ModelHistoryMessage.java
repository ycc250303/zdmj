package com.zdmj.aiService.api;

import java.util.Objects;

/**
 * 模型历史消息，只表达角色和原文，不携带持久化类型。
 *
 * @param role 用户或助手角色
 * @param content 非空白的历史原文
 */
public record ModelHistoryMessage(ModelMessageRole role, String content) {
    public ModelHistoryMessage {
        Objects.requireNonNull(role, "历史角色不能为空");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("历史消息内容不能为空白");
        }
    }
}
