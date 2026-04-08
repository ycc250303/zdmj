package com.zdmj.jobService.dto;

import lombok.Data;

@Data
public class JobCapabilityProfileDTO {
    /**
     * 岗位能力画像ID
     */
    private Long id;

    /**
     * 岗位ID
     */
    private Long jobId;

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
