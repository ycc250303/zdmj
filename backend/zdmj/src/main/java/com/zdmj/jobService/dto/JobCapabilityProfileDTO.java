package com.zdmj.jobService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobCapabilityProfileDTO {
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

    private List<String> strengths;
    private List<String> missingSkills;
    private List<String> weakEvidenceItems;
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
