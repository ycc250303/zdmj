package com.zdmj.jobService.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobCapabilityProfileResponse {
    /**
     * 岗位类型（展示用，hyphen slug）
     */
    private String targetRoleType;

    /**
     * 岗位识别置信度（0~1）。服务端写入，不进 LLM Schema / HTTP JSON。
     */
    @JsonIgnore
    private BigDecimal roleConfidence;

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
     * 岗位已写明的核心要求亮点。
     */
    private List<String> strengths;

    /**
     * 补充要求：JD 未写明、但该方向校招几乎总会考查的隐含核心门槛（JSON 键名沿用 missingSkills）。
     */
    private List<String> missingSkills;

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
