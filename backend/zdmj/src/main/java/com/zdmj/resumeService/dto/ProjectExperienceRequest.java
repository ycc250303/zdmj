package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目经历请求
 *
 * @param id 项目经历ID 更新时必填，创建时不需要
 * @param name 项目名称 创建时必填，更新时可选
 * @param startDate 项目开始时间（格式：YYYY-MM-DD，例如：2020-09-01） 创建时必填，更新时可选
 * @param endDate 项目结束时间（格式：YYYY-MM-DD，例如：2024-06-30） 创建和更新时都可选（进行中可为空）
 * @param role 在项目中的角色和职责（创建时不能为空）
 * @param description 项目描述（创建时不能为空）
 * @param contribution 项目贡献（创建时不能为空）
 * @param techStack 使用的技术（数组，如["React", "TypeScript", "Node.js"]）
 * @param highlights 项目亮点（字符串数组，每项一条）。 更新时省略或为 null 保持原值；空列表表示清空。
 * @param url 项目链接
 */
public record ProjectExperienceRequest(
        @NotNull(message = "项目经历ID不能为空", groups = UpdateGroup.class)
        Long id,
        @NotBlank(message = "项目名称不能为空", groups = CreateGroup.class)
        String name,
        @NotNull(message = "项目开始时间不能为空", groups = CreateGroup.class)
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate endDate,
        @NotBlank(message = "项目角色不能为空", groups = CreateGroup.class)
        String role,
        @NotBlank(message = "项目描述不能为空", groups = CreateGroup.class)
        String description,
        @NotBlank(message = "项目贡献不能为空", groups = CreateGroup.class)
        String contribution,
        List<String> techStack,
        List<String> highlights,
        String url) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public ProjectExperienceRequest {
        techStack = techStack == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(techStack));
        highlights = highlights == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(highlights));
    }
}
