package com.zdmj.resumeService.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 全量保存当前用户简历内容（技能 + 全部经历）。
 */
@Data
public class ResumeContentSaveRequest {

    @NotNull(message = "技能信息不能为空")
    @Valid
    private SkillDTO skill;

    @NotNull(message = "教育经历列表不能为 null")
    @Valid
    private List<EducationDTO> educations = new ArrayList<>();

    @NotNull(message = "工作经历列表不能为 null")
    @Valid
    private List<CareerDTO> careers = new ArrayList<>();

    @NotNull(message = "项目经历列表不能为 null")
    @Valid
    private List<ProjectExperienceDTO> projects = new ArrayList<>();

    @NotNull(message = "获奖信息列表不能为 null")
    @Valid
    private List<AwardDTO> awards = new ArrayList<>();

    @Valid
    private ResumePersonalInfoDTO personalInfo;
}
