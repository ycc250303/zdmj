package com.zdmj.conversationService.entity;

import com.zdmj.common.model.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_vectors")
public class MessageVectors extends BaseEntity {
    /**
     * 向量ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息ID
     */
    private Long messageId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 向量
     */
    private String embedding;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息元数据
     */
    private String metadata;
}
