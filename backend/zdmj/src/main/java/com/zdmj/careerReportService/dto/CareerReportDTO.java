package com.zdmj.careerReportService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 职业发展报告 DTO（响应用）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerReportDTO {

    /**
     * 报告ID
     */
    private Long id;

    /**
     * 岗位ID
     */
    private Long jobId;

    /**
     * 状态：1=草稿/2=已校验/3=已发布/4=校验未通过
     */
    private Integer status;

    /**
     * 完整度评分（0~100）
     */
    private Integer completenessScore;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 是否为该岗位下的最新版本
     */
    private Boolean latest;

    /**
     * 生成/润色时使用的提示词名称
     */
    private String promptName;

    /**
     * 结构化报告正文
     */
    private Map<String, Object> reportContent;

    /**
     * 质量与完整性标记
     */
    private Map<String, Object> qualityFlags;

    /**
     * RAG 命中的知识来源
     */
    private List<Map<String, Object>> knowledgeSources;
}
