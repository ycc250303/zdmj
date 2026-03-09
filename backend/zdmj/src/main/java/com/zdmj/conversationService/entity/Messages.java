package com.zdmj.conversationService.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbObjectTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("messages")
public class Messages extends BaseEntity {
    /**
     * 消息ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 消息角色（1=user用户/2=assistant助手/3=system系统）
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
     * 消息元数据（JSONB格式）
     * 示例：
     * {
     * "tokens": {
     * "prompt": 150,
     * "completion": 200,
     * "total": 350
     * },
     * "model": "qwen",
     * "finish_reason": "stop",
     * "generation_time_ms": 1234
     * }
     */
    @TableField(typeHandler = JsonbObjectTypeHandler.class)
    private String metadata;
}
