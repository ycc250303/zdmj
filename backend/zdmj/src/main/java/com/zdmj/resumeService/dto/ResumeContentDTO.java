package com.zdmj.resumeService.dto;

import java.util.List;
import lombok.Data;

/**
 * 简历完整内容 DTO
 */
@Data
public class ResumeContentDTO {
    /**
     * 简历ID
     */
    private Long id;

    /**
     * 技能清单
     */
    private SkillDTO skill;

    /**
     * 教育经历列表
     */
    private List<EducationDTO> educations;

    /**
     * 工作经历列表
     */
    private List<CareerDTO> careers;

    /**
     * 项目经历列表
     */
    private List<ProjectExperienceDTO> projects;

    /**
     * 获奖信息列表
     */
    private List<AwardDTO> awards;

    /**
     * 基本信息（姓名、电话、个人主页、意向工作城市）
     */
    private ResumePersonalInfoDTO personalInfo;
}
