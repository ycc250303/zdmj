package com.zdmj.conversationService.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ConversationDTO {
    /**
     * 对话ID
     */
    private Long id;

    /**
     * 关联项目ID（可空）
     */
    private Long projectId;

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
}
