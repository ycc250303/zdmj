package com.zdmj.matchService.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 已保存的人岗匹配不可变快照，字段对应匹配记录与报告中的匹配快照。
 * @param id 匹配记录编号，对应 job_student_matches.id
 * @param jobId 岗位编号，对应 job_student_matches.job_id
 * @param targetRoleType 生成时的岗位方向
 * @param overallScore 按四项权重计算的综合评分，范围0～100
 * @param dimensions 维度明细，码值为 basic、professionalSkill、professionalQuality、developmentPotential
 * @param weights 生成时的四项权重
 * @param matchedHighlights 匹配亮点，保留空值与顺序
 * @param criticalGaps 关键差距，保留空值与顺序
 * @param matchedKeywords 已命中的岗位关键词
 * @param missingKeywords 尚未命中的岗位关键词
 * @param keySkillMatchRate 关键技能匹配率，范围0～1
 * @param summary 匹配评价
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobMatchSnapshot(
        Long id,
        Long jobId,
        String targetRoleType,
        Integer overallScore,
        Map<String, MatchDimensionSnapshot> dimensions,
        MatchWeightsSnapshot weights,
        List<String> matchedHighlights,
        List<String> criticalGaps,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        BigDecimal keySkillMatchRate,
        String summary) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public JobMatchSnapshot {
        dimensions = dimensions == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(dimensions));
        matchedHighlights = matchedHighlights == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(matchedHighlights));
        criticalGaps = criticalGaps == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(criticalGaps));
        matchedKeywords = matchedKeywords == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(matchedKeywords));
        missingKeywords = missingKeywords == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(missingKeywords));
    }
}
