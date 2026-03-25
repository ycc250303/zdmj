package com.zdmj.conversationService.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.conversationService.enums.MessageRoleEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体类
 * 对应数据库表：messages
 */
@Data
@TableName("messages")
public class Message {

    /**
     * 消息ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联会话ID（逻辑外键：conversations.id）
     */
    private Long conversationId;

    /**
     * 用户ID（逻辑外键：users.id）
     */
    private Long userId;

    /**
     * 消息角色（1=user/2=assistant/3=system）
     */
    private Integer role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息序号（在会话中的顺序，从1开始）
     */
    private Integer sequence;

    /**
     * 消息创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 获取消息角色枚举（字段仍使用整数存储）
     */
    public MessageRoleEnum getRoleEnum() {
        return MessageRoleEnum.fromCode(this.role);
    }

    /**
     * 设置消息角色枚举（字段仍使用整数存储）
     */
    public void setRoleEnum(MessageRoleEnum roleEnum) {
        this.role = roleEnum != null ? roleEnum.getCode() : null;
    }
}
