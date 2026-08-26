package com.zdmj.resumeService.dto;

import java.util.List;
import lombok.Data;

/**
 * 简历完整内容响应
 */
@Data
public class ResumeContentResponse {
    /**
     * 简历ID
     */
    private Long id;

    /**
     * 技能清单
     */
    private SkillResponse skill;

    /**
     * 教育经历列表
     */
    private List<EducationRequest> educations;

    /**
     * 工作经历列表
     */
    private List<CareerRequest> careers;

    /**
     * 项目经历列表
     */
    private List<ProjectExperienceRequest> projects;

    /**
     * 获奖信息列表
     */
    private List<AwardRequest> awards;

    /**
     * 基本信息（姓名、电话、个人主页、意向工作城市）
     */
    private ResumePersonalInfoDTO personalInfo;
}
