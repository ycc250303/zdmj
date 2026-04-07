package com.zdmj.resumeService.dto;

import lombok.Data;

/**
 * 学生就业能力画像 DTO
 */
@Data
public class StudentCapabilityProfileDTO {
    
    private Long id;
    
    private Long userId;

    private String professionalSkills;

    private String certificates;

    private String innovationAbility;

    private String learningAbility;

    private String pressureResistance;

    private String communicationAbility;

    private String practicalAbility;

    private Integer completenessScore;

    private Integer competitivenessScore;
}
