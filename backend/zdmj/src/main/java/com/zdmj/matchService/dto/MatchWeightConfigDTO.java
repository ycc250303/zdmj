package com.zdmj.matchService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 四维度权重配置。
 *
 * <p>赛题《题目.md》要求：「最后根据当前岗位在不同维度的权重设置进行综合打分综合处理」。
 * 因此本对象既支持后端按 {@code targetRoleType} 路由出默认权重，也支持前端通过请求体覆盖。
 * 字段类型用 {@link BigDecimal} 保证序列化精度。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchWeightConfigDTO {

    /**
     * 基础要求权重（学历 / 证书 / 硬性资质门槛）。
     */
    private BigDecimal basic;

    /**
     * 职业技能权重（专业技能 / 关键词命中）。
     */
    private BigDecimal professionalSkill;

    /**
     * 职业素养权重（沟通 / 抗压等通用素质）。
     */
    private BigDecimal professionalQuality;

    /**
     * 发展潜力权重（创新 / 学习 / 实习/实践能力）。
     */
    private BigDecimal developmentPotential;

    /**
     * 转 Map 视图，便于按维度 code 取值（用于落库 / Prompt 拼装）。
     */
    public Map<String, BigDecimal> toMap() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("basic", basic);
        map.put("professionalSkill", professionalSkill);
        map.put("professionalQuality", professionalQuality);
        map.put("developmentPotential", developmentPotential);
        return map;
    }
}
