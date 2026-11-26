package com.zdmj.matchService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 生成人岗匹配分析请求体。
 *
 * <p>所有字段均为可选；不传 {@code weights} 时使用岗位类型默认权重。</p>
 *
 * @param weights 自定义权重配置，可为 null。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobStudentMatchGenerateRequest(
        MatchWeightConfigResponse weights) {
}
