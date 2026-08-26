package com.zdmj.knowledgeService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

/**
 * 创建/更新知识文档请求
 */
@Data
public class KnowledgeDocumentRequest {
    /**
     * 文档ID
     */
    @NotNull(message = "文档ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 文档标题
     */
    @NotBlank(message = "文档标题不能为空", groups = CreateGroup.class)
    private String title;

    /**
     * 来源类型
     */
    @NotNull(message = "来源类型不能为空", groups = CreateGroup.class)
    private Integer type;

    /**
     * 来源地址
     */
    @NotBlank(message = "来源地址不能为空", groups = CreateGroup.class)
    private String content;
}
