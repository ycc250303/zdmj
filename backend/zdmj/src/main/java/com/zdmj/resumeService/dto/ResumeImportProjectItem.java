package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/** 简历导入的项目经历，模型解析后需要规范化日期与描述。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportProjectItem {
    /** 项目名称，对应 project_experiences.name，规范化时过滤空白名称。 */
    private String name;
    /** 项目角色，对应 project_experiences.role。 */
    private String role;
    /** 开始日期，对应 project_experiences.start_date；模型文本由业务规范化。 */
    private String startDate;
    /** 结束日期，对应 project_experiences.end_date；模型文本由业务规范化。 */
    private String endDate;
    /** 项目描述，对应 project_experiences.description。 */
    private String description;
    /** 个人贡献，对应 project_experiences.contribution。 */
    private String contribution;
    /** 项目使用的技术名称集合，对应 project_experiences.tech_stack。 */
    private List<String> techStack;
    /** 项目亮点集合，对应 project_experiences.highlights；允许省略或空列表。 */
    private List<String> highlights;
    /** 项目地址，对应 project_experiences.url。 */
    private String url;
}
