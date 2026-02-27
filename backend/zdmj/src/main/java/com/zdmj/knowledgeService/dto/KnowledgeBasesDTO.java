package com.zdmj.knowledgeService.dto;

import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeBasesDTO {

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空", groups = CreateGroup.class)
    private String name;

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空", groups = CreateGroup.class)
    private String projectName;

    /**
     * 文件类型
     */
    @NotNull(message = "文件类型不能为空", groups = CreateGroup.class)
    private Integer fileType;

    /**
     * 知识标签（数组）
     */
    @NotEmpty(message = "知识标签不能为空", groups = CreateGroup.class)
    private List<String> tag;

    /**
     * 知识类型
     */
    @NotNull(message = "知识类型不能为空", groups = CreateGroup.class)
    private Integer type;

    /**
     * 文档内容
     */
    @NotBlank(message = "文档内容不能为空", groups = CreateGroup.class)
    private String content;
}
