package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 教育经历DTO
 * 支持创建和更新两种场景，使用验证组区分不同的验证规则
 */
@Data
public class EducationDTO {
    /**
     * 教育经历ID
     * 更新时必填，创建时不需要
     */
    @NotNull(message = "教育经历ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 学校名称
     * 创建时必填，更新时可选
     */
    @NotBlank(message = "学校名称不能为空", groups = CreateGroup.class)
    private String school;

    /**
     * 专业名称
     * 创建时必填，更新时可选
     */
    @NotBlank(message = "专业名称不能为空", groups = CreateGroup.class)
    private String major;

    /**
     * 学历层次（1: 博士, 2: 硕士, 3: 本科, 4: 大专, 5: 高中, 6: 其他）
     * 创建时必填，更新时可选（但如果提供，必须在有效范围内）
     */
    @NotNull(message = "学历层次不能为空", groups = CreateGroup.class)
    @Min(value = 1, message = "学历层次不能小于1", groups = { CreateGroup.class, UpdateGroup.class })
    @Max(value = 6, message = "学历层次不能大于6", groups = { CreateGroup.class, UpdateGroup.class })
    private Integer degree;

    /**
     * 入学时间（格式：YYYY-MM-DD，例如：2020-09-01）
     * 创建时必填，更新时可选
     */
    @NotNull(message = "入学时间不能为空", groups = CreateGroup.class)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * 毕业时间（格式：YYYY-MM-DD，例如：2024-06-30）
     * 创建和更新时都可选（在读情况）
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * 是否在简历中展示
     */
    private Boolean visible = true;

    /**
     * 绩点
     */
    private String gpa;
}
