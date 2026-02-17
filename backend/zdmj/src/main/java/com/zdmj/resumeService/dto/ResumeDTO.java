package com.zdmj.resumeService.dto;

import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 简历实体类
 * 对应数据库表：resumes
 */
@Data
public class ResumeDTO {
    /**
     * 简历ID（主键，自增）
     */
    @NotNull(message = "简历ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 简历名称
     */
    @NotBlank(message = "简历名称不能为空", groups = CreateGroup.class)
    private String name;

    /**
     * 技能清单ID
     */
    @NotNull(message = "技能清单ID不能为空", groups = CreateGroup.class)
    private Long skillId;
}
