package com.zdmj.conversationService.dto;

import com.zdmj.conversationService.enums.MessageRoleEnum;

import java.time.LocalDateTime;

/**
 * 消息响应（镜像当前 Message Entity JSON）
 *
 * @param id 消息编号，对应 messages.id
 * @param conversationId 会话编号
 * @param userId 所属用户编号
 * @param role 消息角色，对应 messages.role：1 用户、2 助手、3 系统
 * @param content 消息正文
 * @param sequence 会话内消息顺序
 * @param createdAt 创建时间
 */
public record MessageResponse(
        Long id,
        Long conversationId,
        Long userId,
        Integer role,
        String content,
        Integer sequence,
        LocalDateTime createdAt) {

    public MessageRoleEnum getRoleEnum() {
        return MessageRoleEnum.fromCode(this.role);
    }
}
