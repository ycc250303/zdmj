package com.zdmj.careerReportService.dto;

import java.util.List;

/**
 * 报告完整性检查结果 DTO
 *
 * @param passed 是否通过完整性检查
 * @param completenessScore 完整度评分（0~100）
 * @param riskLevel 风险等级：low / medium / high
 * @param missingSections 缺失的章节键或展示名
 * @param nonActionableItems 行动计划中不可执行或信息不足的项
 * @param weakEvidenceItems 证据引用薄弱或缺失的项
 */
public record CareerReportCheckResponse(
        Boolean passed,
        Integer completenessScore,
        String riskLevel,
        List<String> missingSections,
        List<String> nonActionableItems,
        List<String> weakEvidenceItems) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public CareerReportCheckResponse {
        missingSections = missingSections == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(missingSections));
        nonActionableItems = nonActionableItems == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(nonActionableItems));
        weakEvidenceItems = weakEvidenceItems == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(weakEvidenceItems));
    }
}
