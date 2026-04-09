package com.zdmj.resumeService.dto;

import lombok.Data;

/**
 * 学生就业能力画像 DTO
 */
@Data
public class StudentCapabilityProfileDTO {
    /**
     * 学生就业能力画像ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;

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

    /**
     * 完整度评分
     */
    private Integer completenessScore;

    /**
     * 竞争力评分
     */
    private Integer competitivenessScore;
}
