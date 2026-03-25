package com.zdmj.conversationService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "conversations", autoResultMap = true)
public class Conversation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（逻辑外键：users.id）
     */
    private Long userId;

    /**
     * 关联项目ID（可空）
     */
    private Long projectId;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 对话配置（JSONB对象）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    /**
     * 上下文信息（JSONB数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> context;

    /**
     * 消息总数
     */
    private Integer messageCount;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageAt;
}
