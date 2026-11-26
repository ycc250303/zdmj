package com.zdmj.resumeService.dto;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 一项技能的创建或更新请求。
 *
 * @param id 技能编号。更新时不能为空，创建时不需要。
 * @param name 技能名称。创建和更新都不能为空白，最长 255。
 * @param description 技能说明。可省略；空白保存为 null，最长 500。
 */
public record SkillRequest(
        @NotNull(message = "技能ID不能为空", groups = UpdateGroup.class)
        Long id,
        @NotBlank(message = "技能名称不能为空", groups = {CreateGroup.class, UpdateGroup.class})
        @Size(max = 255, message = "技能名称长度不能超过255个字符", groups = {CreateGroup.class, UpdateGroup.class})
        String name,
        @Size(max = 500, message = "技能说明长度不能超过500个字符", groups = {CreateGroup.class, UpdateGroup.class})
        String description) {
}
