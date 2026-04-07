package com.zdmj.knowledgeService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

@Data
public class KnowledgeDocumentDTO {
    /**
     * 文档ID
     */
    @NotNull(message = "文档ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 知识库名称
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
