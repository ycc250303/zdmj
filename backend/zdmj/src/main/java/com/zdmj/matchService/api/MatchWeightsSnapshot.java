package com.zdmj.matchService.api;

import java.math.BigDecimal;

/**
 * 不可变匹配分析结果。
 * @param basic 基础要求权重
 * @param professionalSkill 职业技能权重
 * @param professionalQuality 职业素养权重
 * @param developmentPotential 发展潜力权重
 */
public record MatchWeightsSnapshot(
        BigDecimal basic,
        BigDecimal professionalSkill,
        BigDecimal professionalQuality,
        BigDecimal developmentPotential) {
}
