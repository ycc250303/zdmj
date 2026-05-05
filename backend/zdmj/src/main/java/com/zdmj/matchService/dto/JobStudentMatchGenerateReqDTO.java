package com.zdmj.matchService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 生成人岗匹配分析请求体。
 *
 * <p>所有字段均为可选：</p>
 * <ul>
 *   <li>不传 {@code weights} 时，使用 {@link com.zdmj.matchService.service.impl.MatchWeightResolver}
 *       按岗位类型解析的默认权重；</li>
 *   <li>传入 {@code weights} 时会做归一化（总和归到 1.0），再用于综合打分。</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobStudentMatchGenerateReqDTO {

    /**
     * 自定义权重配置，可为 null。
     */
    private MatchWeightConfigDTO weights;
}
