package com.zdmj.resumeService.dto;

import java.util.List;

import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


/**
 * 技能实体类
 * 对应数据库表：skills
 */
@Data
public class SkillDTO {
    /**
     * 技能ID（主键，自增）
     */
    @NotNull(message = "技能ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 技能清单名称
     */
    @NotBlank(message = "技能清单名称不能为空", groups = CreateGroup.class)
    private String name;

    /**
     * 职业技能描述（JSONB格式）
     * 示例：[{"type": "前端框架", "content": ["React", "Vue.js"]}, ...]
     */
    @Valid
    @NotEmpty(message = "技能内容不能为空", groups = CreateGroup.class)
    private List<SkillItemDTO> content;
}
