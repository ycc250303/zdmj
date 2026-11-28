package com.zdmj.matchService.api;

import java.util.List;

/**
 * 不可变匹配分析结果。
 * @param jobSide 岗位要求
 * @param studentSide 学生表现
 * @param score 维度评分，范围0～100
 * @param gap 差距描述
 * @param evidence 证据片段，保留空值及顺序
 */
public record MatchDimensionSnapshot(
        String jobSide,
        String studentSide,
        Integer score,
        String gap,
        List<String> evidence) {
    /** 复制集合结构，保留 null 与元素语义。 */
    public MatchDimensionSnapshot {
        evidence = evidence == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(evidence));
    }
}
