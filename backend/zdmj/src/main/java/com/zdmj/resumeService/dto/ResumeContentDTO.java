package com.zdmj.resumeService.dto;

import java.util.List;
import lombok.Data;

/**
 * 简历实体类
 * 对应数据库表：resumes
 */
@Data
public class ResumeContentDTO {
    /**
     * 简历ID（主键，自增）
     */
    private Long id;

    /**
     * 简历名称
     */
    private String name;

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
}
