package com.zdmj.matchService.enums;

import java.util.List;
import lombok.Getter;

/**
 * 人岗匹配的四个评分维度。
 *
 * <p>赛题《题目.md》「人岗匹配准确性判断」明确要求：从基础要求 / 职业技能 / 职业素养 / 发展潜力
 * 四个方面进行多维度能力分析。本枚举把每个维度映射回学生/岗位画像中已有的「七维顶层字段」，
 * 用于 Prompt 上下文拼装与权重路由。</p>
 */
@Getter
public enum MatchDimension {

    /**
     * 基础要求：学历、岗位硬性资质门槛与学生荣誉/竞赛成果。
     */
    BASIC(
            "basic",
            "基础要求",
            "学历、岗位硬性资质门槛与学生荣誉/竞赛成果",
            List.of("honorsAndAwards")),

    /**
     * 职业技能：专业技能、技术栈、关键词命中。
     */
    PROFESSIONAL_SKILL(
            "professionalSkill",
            "职业技能",
            "专业技能、技术栈、岗位关键词命中度",
            List.of("professionalSkills")),

    /**
     * 职业素养：沟通能力、抗压能力等通用素质。
     */
    PROFESSIONAL_QUALITY(
            "professionalQuality",
            "职业素养",
            "沟通能力、抗压能力等通用职业素质",
            List.of("communicationAbility", "pressureResistance")),

    /**
     * 发展潜力：创新能力、学习能力、实习/实践能力。
     */
    DEVELOPMENT_POTENTIAL(
            "developmentPotential",
            "发展潜力",
            "创新能力、学习能力、实习/实践能力等成长性",
            List.of("innovationAbility", "learningAbility", "practicalAbility"));

    /**
     * 维度代号（与 Prompt / DTO JSON key 一致，使用 lowerCamelCase）。
     */
    private final String code;
    /**
     * 维度中文名（用于前端展示与 Prompt 引导）。
     */
    private final String displayName;
    /**
     * 维度说明（用于 Prompt 中告诉 LLM 该维度的考察范围）。
     */
    private final String description;
    /**
     * 该维度对应的「七维画像」字段列表（lowerCamelCase）。
     */
    private final List<String> profileFields;

    MatchDimension(String code, String displayName, String description, List<String> profileFields) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.profileFields = profileFields;
    }

    /**
     * 根据 code 解析维度，未知则返回 null。
     */
    public static MatchDimension fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MatchDimension d : values()) {
            if (d.code.equalsIgnoreCase(code.trim())) {
                return d;
            }
        }
        return null;
    }
}
