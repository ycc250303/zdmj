package com.zdmj.resumeService.dto;

import java.util.List;

import lombok.Data;

/**
 * 技能响应（仅 id + content，不含 userId）
 */
@Data
public class SkillResponse {

    private Long id;

    private List<SkillItemDTO> content;
}
