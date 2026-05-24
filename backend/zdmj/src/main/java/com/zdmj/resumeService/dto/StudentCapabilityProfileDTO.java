package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 学生就业能力画像 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentCapabilityProfileDTO {
    /**
     * 简历适配岗位类型（展示用，使用提示词名称）
     */
    private String targetRoleType;

    /**
     * 专业技能：2～4 句中文，须结合简历中的课程/项目/技术栈写具体证据，避免只堆砌关键词。
     */
    private String professionalSkills;

    /**
     * 获奖经历：在校荣誉、竞赛获奖、奖学金等；无则写「无」或「简历未体现」。
     */
    @JsonAlias("certificates")
    private String honorsAndAwards;

    /**
     * 创新能力：结合竞赛/课题/项目中的创新点、个人角色与可验证结果（勿空洞套话）。
     */
    private String innovationAbility;

    /**
     * 学习能力：从自学内容、技术栈扩展、问题解决过程等提取可追问证据。
     */
    private String learningAbility;

    /**
     * 抗压能力：时间紧/任务重/多线程协作等场景中的职责与交付，尽量量化或写清边界。
     */
    private String pressureResistance;

    /**
     * 沟通能力：协作、评审、跨角色对接等经历；无证据则写「简历未体现」。
     */
    private String communicationAbility;

    /**
     * 实习/实践能力：项目背景—个人职责—技术实现—结果；突出个人贡献与可量化产出。
     */
    private String practicalAbility;

    /**
     * 综合竞争力（0～100），由后端根据 scoreDetail 五项之和计算；API 中作为主分数展示。
     */
    private Integer competitivenessScore;

    /**
     * 岗位专项评估分项（兼容 resume-analysis-* 提示词输出）
     */
    private ScoreDetail scoreDetail;

    /**
     * 优势亮点：每条须为「概括 + 简历具体证据（项目名/技术栈/方案/指标）」，禁止空泛套话。
     */
    private List<String> strengths;

    /**
     * 改进建议（含技能缺失、证据不足、项目/表达/结构等，见 resume-analysis 提示词）
     */
    private List<Suggestion> suggestions;

    /**
     * 一句话总结
     */
    private String summary;

    /**
     * 兼容模型返回的嵌套 capabilityProfile 字段，并回填到当前 DTO 扁平字段
     */
    @JsonProperty("capabilityProfile")
    public void setCapabilityProfile(Map<String, String> capabilityProfile) {
        if (capabilityProfile == null) {
            return;
        }
        this.professionalSkills = capabilityProfile.get("professionalSkills");
        String honors = capabilityProfile.get("honorsAndAwards");
        this.honorsAndAwards = honors != null ? honors : capabilityProfile.get("certificates");
        this.innovationAbility = capabilityProfile.get("innovationAbility");
        this.learningAbility = capabilityProfile.get("learningAbility");
        this.pressureResistance = capabilityProfile.get("pressureResistance");
        this.communicationAbility = capabilityProfile.get("communicationAbility");
        this.practicalAbility = capabilityProfile.get("practicalAbility");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreDetail {
        /**
         * 项目经验（0-40）
         */
        @JsonAlias("jobMatchTechDepthScore")
        private Integer projectExperienceScore;
        /**
         * 技能匹配（0-20）
         */
        @JsonAlias("projectPracticeScore")
        private Integer skillMatchScore;
        /**
         * 内容完整性（0-15）
         */
        private Integer contentCompletenessScore;
        /**
         * 结构清晰度（0-15）
         */
        @JsonAlias("structureExpressionScore")
        private Integer structureClarityScore;
        /**
         * 表达专业性（0-10）
         */
        @JsonAlias("professionalPotentialScore")
        private Integer expressionProfessionalismScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Suggestion {
        /** 技能缺失 | 证据不足 | 项目 | 表达 | 结构 | 职业素养 */
        private String category;
        private String priority;
        /** 问题描述：缺什么 / 哪里薄弱 / 与简历哪处相关 */
        private String issue;
        /** 可执行的改进动作 */
        private String recommendation;
    }
}
