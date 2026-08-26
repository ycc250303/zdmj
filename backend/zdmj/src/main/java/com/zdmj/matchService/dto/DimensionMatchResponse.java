package com.zdmj.matchService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/**
 * 单一维度的匹配对比结构。
 *
 * <p>用于「基础要求 / 职业技能 / 职业素养 / 发展潜力」四维中每一维的双向呈现：</p>
 * <ul>
 *   <li>{@code jobSide}：岗位画像中该维度的要求摘要</li>
 *   <li>{@code studentSide}：学生画像中该维度的表现摘要</li>
 *   <li>{@code score}：模型对该维度匹配度的打分（0~100）</li>
 *   <li>{@code gap}：差距描述（自然语言）</li>
 *   <li>{@code evidence}：支撑判断的关键证据片段（命中或差距）</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionMatchResponse {

    /**
     * 岗位侧要求摘要（2~4 句中文）。
     */
    private String jobSide;

    /**
     * 学生侧表现摘要（2~4 句中文）。
     */
    private String studentSide;

    /**
     * 该维度匹配度评分（0~100，整数）。
     */
    private Integer score;

    /**
     * 差距描述（围绕该维度，指明学生离岗位要求的具体落差）。
     */
    private String gap;

    /**
     * 支撑该维度判断的证据片段（命中关键词、原文摘录、缺失项）。
     */
    private List<String> evidence;
}
