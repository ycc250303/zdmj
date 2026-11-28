package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 简历导入的技能条目，模型解析后需要规范化技能名称与描述。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportSkillItem {
    /** 技能名称，对应 skills.name，规范化时过滤空白名称。 */
    private String name;
    /** 技能说明，对应 skills.description。 */
    private String description;
}
