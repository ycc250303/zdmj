package com.zdmj.conversationService.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbObjectTypeHandler;
import com.zdmj.common.typehandler.JsonbListTypeHandler;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversations")
public class Conversations extends BaseEntity {
    /**
     * 会话ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目ID（NULL表示通用对话，有值表示项目关联对话）
     */
    private Long projectId;

    /**
     * 会话标题（可由AI生成或用户自定义）
     */
    private String title;

    /**
     * 使用的AI模型
     */
    private String model;

    /**
     * 对话配置（JSONB格式）
     * 示例：
     * {
     * "temperature": 0.7,
     * "max_tokens": 2000,
     * "top_p": 1.0,
     * "frequency_penalty": 0,
     * "presence_penalty": 0
     * }
     */
    @TableField(typeHandler = JsonbObjectTypeHandler.class)
    private String config;

    /**
     * 上下文信息（JSONB格式，可关联知识库等，用于RAG检索）
     * 示例：
     * [
     * {
     * "type": "knowledge_base",
     * "id": 456,
     * "name": "知识库名称"
     * }
     * ]
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private String context;

    /**
     * 消息总数
     */
    private Integer messageCount;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageAt;
}
