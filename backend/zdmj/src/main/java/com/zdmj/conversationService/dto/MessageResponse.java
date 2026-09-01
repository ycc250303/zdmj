package com.zdmj.conversationService.dto;

import com.zdmj.conversationService.enums.MessageRoleEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息响应（镜像当前 Message Entity JSON）
 */
@Data
public class MessageResponse {

    private Long id;

    private Long conversationId;

    private Long userId;

    private Integer role;

    private String content;

    private Integer sequence;

    private LocalDateTime createdAt;

    public MessageRoleEnum getRoleEnum() {
        return MessageRoleEnum.fromCode(this.role);
    }

    public void setRoleEnum(MessageRoleEnum roleEnum) {
        this.role = roleEnum != null ? roleEnum.getCode() : null;
    }
}
