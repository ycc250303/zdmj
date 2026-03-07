package com.zdmj.conversationService.dto;

import lombok.Data;

@Data
public class ConversationsDTO {

    /**
     * 会话ID（仅用于内部传递，创建时不需要）
     */
    private Long id;

    /**
     * 项目ID（可选，如果提供则必须存在且属于当前用户）
     */
    private Long projectId;
}
