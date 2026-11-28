package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 简历导入的工作经历，模型解析后需要规范化日期与空白内容。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportCareerItem {
    /** 单位名称，对应 careers.company，规范化时过滤空白单位。 */
    private String company;
    /** 岗位名称，对应 careers.position。 */
    private String position;
    /** 开始日期，对应 careers.start_date；模型文本由业务规范化。 */
    private String startDate;
    /** 结束日期，对应 careers.end_date；模型文本由业务规范化。 */
    private String endDate;
    /** 工作内容，对应 careers.details。 */
    private String details;
}
