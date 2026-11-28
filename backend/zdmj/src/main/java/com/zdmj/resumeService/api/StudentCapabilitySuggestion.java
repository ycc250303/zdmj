package com.zdmj.resumeService.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 能力画像改进建议。
 *
 * @param category 技能缺失 | 证据不足 | 项目 | 表达 | 结构 | 职业素养
 * @param priority 建议优先级
 * @param issue 问题描述：缺什么 / 哪里薄弱 / 与简历哪处相关
 * @param recommendation 可执行的改进动作
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentCapabilitySuggestion(
        String category,
        String priority,
        String issue,
        String recommendation) {
}
