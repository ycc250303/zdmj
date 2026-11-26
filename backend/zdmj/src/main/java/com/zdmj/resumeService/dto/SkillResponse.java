package com.zdmj.resumeService.dto;

/**
 * 一项技能的响应。
 *
 * @param id 技能编号。
 * @param name 技能名称。
 * @param description 技能说明。没有说明时为空。
 */
public record SkillResponse(
        Long id,
        String name,
        String description) {
}
