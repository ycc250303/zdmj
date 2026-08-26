package com.zdmj.jobService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobCapabilityProfileResponse {
    /**
     * 岗位类型（展示用，使用提示词名称）
     */
    private String targetRoleType;

    /**
     * 岗位要求画像七维（顶层输出，面向求职者）
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
     * 实践能力
     */
    private String practicalAbility;

    /**
     * 岗位优势亮点
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
     * 一句话总结
     */
    private String summary;

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
    public static class CapabilityProfile {
        private String professionalSkills;
        private String certificates;
        private String innovationAbility;
        private String learningAbility;
        private String pressureResistance;
        private String communicationAbility;
        private String practicalAbility;
    }

}
