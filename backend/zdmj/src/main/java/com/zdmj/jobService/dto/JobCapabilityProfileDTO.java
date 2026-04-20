package com.zdmj.jobService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobCapabilityProfileDTO {
    @JsonProperty(access = Access.WRITE_ONLY)
    private Long id;

    @JsonProperty(access = Access.WRITE_ONLY)
    private Long jobId;

    /**
     * 岗位要求画像七维（顶层输出，面向求职者）
     */
    private String professionalSkills;
    private String certificates;
    private String innovationAbility;
    private String learningAbility;
    private String pressureResistance;
    private String communicationAbility;
    private String practicalAbility;

    /**
     * 岗位分类与提示词信息
     */
    @JsonProperty(access = Access.WRITE_ONLY)
    private String targetRoleCode;

    @JsonProperty(access = Access.WRITE_ONLY)
    private BigDecimal roleConfidence;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String promptName;

    private List<String> strengths;
    private List<String> missingSkills;
    private List<String> weakEvidenceItems;
    private String summary;

    /**
     * 兼容模型若返回 capabilityProfile 嵌套对象，回填至顶层七维
     */
    @JsonProperty(access = Access.WRITE_ONLY)
    private CapabilityProfile capabilityProfile;

    @JsonProperty("capabilityProfile")
    public void setCapabilityProfile(CapabilityProfile capabilityProfile) {
        this.capabilityProfile = capabilityProfile;
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
