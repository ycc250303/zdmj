package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 项目经历DTO
 */
@Data
public class ProjectExperienceDTO {
    /**
     * 项目经历ID
     * 更新时必填，创建时不需要
     */
    @NotNull(message = "项目经历ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 项目名称
     * 创建时必填，更新时可选
     */
    @NotBlank(message = "项目名称不能为空", groups = CreateGroup.class)
    private String name;

    /**
     * 项目开始时间（格式：YYYY-MM-DD，例如：2020-09-01）
     * 创建时必填，更新时可选
     */
    @NotNull(message = "项目开始时间不能为空", groups = CreateGroup.class)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * 项目结束时间（格式：YYYY-MM-DD，例如：2024-06-30）
     * 创建和更新时都可选（进行中可为空）
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * 在项目中的角色和职责
     */
    private String role;

    /**
     * 项目描述
     */
    @NotBlank(message = "项目描述不能为空", groups = CreateGroup.class)
    private String description;

    /**
     * 技术栈（JSONB数组，如["React", "TypeScript", "Node.js"]）
     */
    private String techStack;

    /**
     * 项目亮点（JSONB数组，包含技术难点、成果等）
     */
    private String highlights;

    /**
     * 项目链接
     */
    private String url;

    /**
     * 是否在简历中展示
     */
    private Boolean visible = true;
}
