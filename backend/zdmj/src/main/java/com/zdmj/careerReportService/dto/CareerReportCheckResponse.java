package com.zdmj.careerReportService.dto;

import java.util.List;
import lombok.Data;

/**
 * 报告完整性检查结果 DTO
 */
@Data
public class CareerReportCheckResponse {

    /**
     * 是否通过完整性检查
     */
    private Boolean passed;

    /**
     * 完整度评分（0~100）
     */
    private Integer completenessScore;

    /**
     * 风险等级：low / medium / high
     */
    private String riskLevel;

    /**
     * 缺失的章节键或展示名
     */
    private List<String> missingSections;

    /**
     * 行动计划中不可执行或信息不足的项
     */
    private List<String> nonActionableItems;

    /**
     * 证据引用薄弱或缺失的项
     */
    private List<String> weakEvidenceItems;
}
