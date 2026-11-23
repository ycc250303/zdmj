package com.zdmj.careerReportService.dto;

import java.util.Map;

/**
 * 手动编辑报告请求体
 *
 * @param reportContent 手动编辑后的结构化正文。
 */
public record CareerReportUpdateRequest(
        Map<String, Object> reportContent) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public CareerReportUpdateRequest {
        reportContent = reportContent == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(reportContent));
    }
}
