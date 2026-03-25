package com.zdmj.resumeService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 技能项内容（嵌套对象）
 */
@Data
public class SkillItemDTO {
    /**
     * 技能类型（如：前端框架、开发语言等）
     */
    @NotBlank(message = "技能类型不能为空")
    private String type;

    /**
     * 技能内容数组（如：["React", "Vue.js"]）
     */
    @NotEmpty(message = "技能内容不能为空")
    private List<@NotBlank(message = "技能内容项不能为空") String> content;
}