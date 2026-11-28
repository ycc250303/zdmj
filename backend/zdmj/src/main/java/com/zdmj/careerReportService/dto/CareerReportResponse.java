package com.zdmj.careerReportService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import com.zdmj.knowledgeService.api.KnowledgeReference;
import java.util.Map;

/**
 * 职业发展报告 DTO（响应用）
 *
 * @param id 报告ID
 * @param jobId 岗位ID
 * @param status 状态：1=草稿/2=已校验/3=已发布/4=校验未通过
 * @param completenessScore 完整度评分（0~100）
 * @param promptName 生成/润色时使用的提示词名称
 * @param reportContent 结构化报告正文
 * @param qualityFlags 质量与完整性标记
 * @param knowledgeSources RAG 命中的知识来源
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CareerReportResponse(
        Long id,
        Long jobId,
        Integer status,
        Integer completenessScore,
        String promptName,
        Map<String, Object> reportContent,
        Map<String, Object> qualityFlags,
        List<KnowledgeReference> knowledgeSources) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public CareerReportResponse {
        reportContent = reportContent == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(reportContent));
        qualityFlags = qualityFlags == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(qualityFlags));
        knowledgeSources = knowledgeSources == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(knowledgeSources));
    }
}
