package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 简历导入的教育经历，模型解析后需要规范化日期与学历。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportEducationItem {
    /** 学校名称，对应 educations.school，规范化时过滤空白学校。 */
    private String school;
    /** 专业名称，对应 educations.major。 */
    private String major;
    /** 学历码，对应 educations.degree：1博士、2硕士、3本科、4大专、5高中、6其他。 */
    private Integer degree;
    /** 开始日期，对应 educations.start_date；模型文本由业务规范化。 */
    private String startDate;
    /** 结束日期，对应 educations.end_date；模型文本由业务规范化。 */
    private String endDate;
    /** 绩点文本，对应 educations.gpa。 */
    private String gpa;
}
