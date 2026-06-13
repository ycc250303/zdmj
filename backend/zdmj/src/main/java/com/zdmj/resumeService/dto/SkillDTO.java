package com.zdmj.resumeService.dto;

import java.util.List;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 技能 DTO（一用户一份技能清单，仅维护 content）
 */
@Data
public class SkillDTO {
    /**
     * 技能ID（更新时不能为空）
     */
    @NotNull(message = "技能ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 职业技能描述（结构化对象数组）
     */
    @Valid
    @NotEmpty(message = "技能内容不能为空", groups = CreateGroup.class)
    private List<SkillItemDTO> content;
}
