package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 能力画像评分明细。
 *
 * @param projectExperienceScore 项目经验（0-40）
 * @param skillMatchScore 技能匹配（0-20）
 * @param contentCompletenessScore 内容完整性（0-15）
 * @param structureClarityScore 结构清晰度（0-15）
 * @param expressionProfessionalismScore 表达专业性（0-10）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentCapabilityScoreDetail(
        @JsonAlias("jobMatchTechDepthScore")
        Integer projectExperienceScore,
        @JsonAlias("projectPracticeScore")
        Integer skillMatchScore,
        Integer contentCompletenessScore,
        @JsonAlias("structureExpressionScore")
        Integer structureClarityScore,
        @JsonAlias("professionalPotentialScore")
        Integer expressionProfessionalismScore) {
}
