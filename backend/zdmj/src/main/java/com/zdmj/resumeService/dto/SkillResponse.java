package com.zdmj.resumeService.dto;

import lombok.Data;

/**
 * 一项技能的响应。
 */
@Data
public class SkillResponse {

    /**
     * 技能编号。
     */
    private Long id;

    /**
     * 技能名称。
     */
    private String name;

    /**
     * 技能说明。没有说明时为空。
     */
    private String description;
}
