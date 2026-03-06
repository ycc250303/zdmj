package com.zdmj.conversationService.dto;

import lombok.Data;

@Data
public class ConversationsDTO {

    /**
     * 会话ID（仅用于内部传递，创建时不需要）
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 使用的AI模型（创建时可选，默认为"qwen"）
     */
    private String model;

    /**
     * 对话配置
     */
    private String config;
}
