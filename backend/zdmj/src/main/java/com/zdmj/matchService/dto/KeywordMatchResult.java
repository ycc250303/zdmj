package com.zdmj.matchService.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.math.BigDecimal;

/**
 * 关键词匹配计算结果。
 * @param matched 已匹配关键词，保留原有顺序
 * @param missing 未匹配关键词，保留原有顺序
 * @param rate 匹配比例，范围为零到一
 */
public record KeywordMatchResult(List<String> matched, List<String> missing, BigDecimal rate) {
    public KeywordMatchResult {
        matched = matched == null ? null : Collections.unmodifiableList(new ArrayList<>(matched));
        missing = missing == null ? null : Collections.unmodifiableList(new ArrayList<>(missing));
    }
}
