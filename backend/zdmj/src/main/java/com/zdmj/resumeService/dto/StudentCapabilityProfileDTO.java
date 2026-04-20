package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * 学生就业能力画像 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentCapabilityProfileDTO {
    /**
     * 专业技能：2～4 句中文，须结合简历中的课程/项目/技术栈写具体证据，避免只堆砌关键词。
     */
    private String professionalSkills;

    /**
     * 证书：说明有无证书、名称与含金量；无则写「无」并简述是否影响岗位判断。
     */
    private String certificates;

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
     * 简历完整度总评（0～100）：教育/技能/项目/实习等是否齐全、能否支撑评估；须由模型显式打分，勿默认 0。
     */
    private Integer completenessScore;

    /**
     * 综合竞争力（0～100），与 scoreDetail 五项之和及对外主分一致；API 中作为主分数展示。
     */
    private Integer competitivenessScore;

    /**
     * 岗位专项评估分项（兼容 resume-analysis-* 提示词输出）
     */
    private ScoreDetail scoreDetail;

    /**
     * 简历优势点
     */
    private List<String> strengths;

    /**
     * 缺失技能项
     */
    private List<String> missingSkills;

    /**
     * 证据不足项
     */
    private List<String> weakEvidenceItems;

    /**
     * 改进建议
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
    public void setCapabilityProfile(CapabilityProfile capabilityProfile) {
        if (capabilityProfile == null) {
            return;
        }
        this.professionalSkills = capabilityProfile.getProfessionalSkills();
        this.certificates = capabilityProfile.getCertificates();
        this.innovationAbility = capabilityProfile.getInnovationAbility();
        this.learningAbility = capabilityProfile.getLearningAbility();
        this.pressureResistance = capabilityProfile.getPressureResistance();
        this.communicationAbility = capabilityProfile.getCommunicationAbility();
        this.practicalAbility = capabilityProfile.getPracticalAbility();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreDetail {
        /**
         * 岗位匹配技术深度评分
         */ 
        private Integer jobMatchTechDepthScore;
        /**
         * 项目实践评分
         */
        private Integer projectPracticeScore;
        /**
         * 内容完整度评分
         */
        private Integer contentCompletenessScore;
        /**
         * 结构表达评分
         */
        private Integer structureExpressionScore;
        /**
         * 职业素养评分
         */
        private Integer professionalPotentialScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CapabilityProfile {
        /**
         * 专业技能
         */
        private String professionalSkills;
        /**
         * 证书
         */
        private String certificates;
        /**
         * 创新能力
         */
        private String innovationAbility;
        /**
         * 学习能力
         */
        private String learningAbility;
        /**
         * 抗压能力
         */
        private String pressureResistance;
        /**
         * 沟通能力
         */
        private String communicationAbility;
        /**
         * 实习能力
         */
        private String practicalAbility;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Suggestion {
        private String category;
        private String priority;
        private String issue;
        private String recommendation;
    }
}
