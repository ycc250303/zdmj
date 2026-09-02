package com.zdmj.matchService.service.impl;

import com.zdmj.common.ai.JobRole;
import com.zdmj.matchService.dto.MatchWeightConfigResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 按岗位类型解析默认权重，并对前端传入的自定义权重做校验与归一化。
 *
 * <p>综合分按当前岗位在各维度上的权重加权得到。本工具负责默认权重路由与自定义权重归一化。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>各岗位的默认权重之和恰好为 1.0；</li>
 *   <li>前端覆盖时允许仅传部分字段，缺失字段从默认值补齐；</li>
 *   <li>负值或非法值视为 0，最后整体归一化到 1.0；</li>
 *   <li>若全部为 0，则回退默认值，避免出现「全维零权重」。</li>
 * </ul>
 */
public final class MatchWeightResolver {

    private MatchWeightResolver() {
    }

    /** 通用兜底权重：技能 0.40 / 潜力 0.25 / 基础 0.20 / 素养 0.15。 */
    private static final MatchWeightConfigResponse DEFAULT = of("0.20", "0.40", "0.15", "0.25");

    /**
     * 各岗位类型的默认权重表。
     *
     * <p>设计依据：技术岗位整体强调专业技能（≥0.40），算法/AI 岗位发展潜力权重相对更高，
     * 测试岗位职业素养权重适当抬升以反映质量与协作要求。</p>
     */
    private static final Map<JobRole, MatchWeightConfigResponse> ROLE_WEIGHTS = Map.ofEntries(
            Map.entry(JobRole.JAVA, of("0.20", "0.45", "0.15", "0.20")),
            Map.entry(JobRole.FRONTEND, of("0.20", "0.45", "0.15", "0.20")),
            Map.entry(JobRole.CPP, of("0.20", "0.50", "0.10", "0.20")),
            Map.entry(JobRole.SOFTWARE_TEST, of("0.20", "0.35", "0.25", "0.20")),
            Map.entry(JobRole.AI_AGENT, of("0.15", "0.40", "0.10", "0.35")),
            Map.entry(JobRole.ALGORITHM, of("0.20", "0.40", "0.10", "0.30")),
            Map.entry(JobRole.DATA_ANALYST, of("0.20", "0.40", "0.20", "0.20")),
            Map.entry(JobRole.BIG_DATA, of("0.20", "0.45", "0.15", "0.20")),
            Map.entry(JobRole.DEVOPS_SRE, of("0.20", "0.45", "0.20", "0.15")),
            Map.entry(JobRole.CYBERSECURITY, of("0.25", "0.40", "0.20", "0.15")));

    /**
     * 取该岗位类型的默认权重（拷贝返回，避免外部修改静态常量）。
     */
    public static MatchWeightConfigResponse defaultFor(JobRole role) {
        MatchWeightConfigResponse base = role == null ? DEFAULT : ROLE_WEIGHTS.getOrDefault(role, DEFAULT);
        return copy(base);
    }

    /**
     * 解析最终生效的权重：若 override 不为空，先用 override 覆盖默认值，再归一化；否则直接返回默认。
     *
     * @param role     岗位角色
     * @param override 前端传入的自定义权重（可能为 null 或部分字段为 null）
     * @return 归一化后的权重（总和 = 1.00），保留 4 位小数
     */
    public static MatchWeightConfigResponse resolve(JobRole role, MatchWeightConfigResponse override) {
        MatchWeightConfigResponse base = defaultFor(role);
        if (override == null) {
            return normalize(base, role);
        }

        if (override.getBasic() != null) {
            base.setBasic(override.getBasic());
        }
        if (override.getProfessionalSkill() != null) {
            base.setProfessionalSkill(override.getProfessionalSkill());
        }
        if (override.getProfessionalQuality() != null) {
            base.setProfessionalQuality(override.getProfessionalQuality());
        }
        if (override.getDevelopmentPotential() != null) {
            base.setDevelopmentPotential(override.getDevelopmentPotential());
        }

        return normalize(base, role);
    }

    /**
     * 归一化：总和归到 1.00；负值置 0；若总和仍为 0 则回退到该岗位类型的默认权重。
     *
     * <p>「全零回退」选择回退到 {@code role} 的默认而非全局默认 —— 因为请求已经显式指定了岗位，
     * 全零是用户的误用，但岗位路由信息仍应当被尊重。</p>
     */
    private static MatchWeightConfigResponse normalize(MatchWeightConfigResponse w, JobRole role) {
        BigDecimal a = nonNegative(w.getBasic());
        BigDecimal b = nonNegative(w.getProfessionalSkill());
        BigDecimal c = nonNegative(w.getProfessionalQuality());
        BigDecimal d = nonNegative(w.getDevelopmentPotential());
        BigDecimal sum = a.add(b).add(c).add(d);

        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            return defaultFor(role);
        }

        return new MatchWeightConfigResponse(
                a.divide(sum, 4, RoundingMode.HALF_UP),
                b.divide(sum, 4, RoundingMode.HALF_UP),
                c.divide(sum, 4, RoundingMode.HALF_UP),
                d.divide(sum, 4, RoundingMode.HALF_UP));
    }

    /**
     * 按权重把四维度评分加权为综合分（0~100，整数）。
     */
    public static int weightedOverall(MatchWeightConfigResponse weights, int basic, int skill, int quality, int potential) {
        BigDecimal score = BigDecimal.valueOf(basic).multiply(nonNegative(weights.getBasic()))
                .add(BigDecimal.valueOf(skill).multiply(nonNegative(weights.getProfessionalSkill())))
                .add(BigDecimal.valueOf(quality).multiply(nonNegative(weights.getProfessionalQuality())))
                .add(BigDecimal.valueOf(potential).multiply(nonNegative(weights.getDevelopmentPotential())));
        int rounded = score.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, rounded));
    }

    private static BigDecimal nonNegative(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return v;
    }

    private static MatchWeightConfigResponse of(String basic, String skill, String quality, String potential) {
        return new MatchWeightConfigResponse(
                new BigDecimal(basic),
                new BigDecimal(skill),
                new BigDecimal(quality),
                new BigDecimal(potential));
    }

    private static MatchWeightConfigResponse copy(MatchWeightConfigResponse w) {
        return new MatchWeightConfigResponse(
                w.getBasic(),
                w.getProfessionalSkill(),
                w.getProfessionalQuality(),
                w.getDevelopmentPotential());
    }
}
