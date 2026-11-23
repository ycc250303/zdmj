package com.zdmj.resumeService.dto;

import java.util.List;

/**
 * 简历完整内容响应
 *
 * @param skills 技能列表。没有技能时为空列表。
 * @param educations 教育经历列表
 * @param careers 工作经历列表
 * @param projects 项目经历列表
 * @param awards 获奖信息列表
 * @param personalInfo 基本信息（姓名、电话、个人主页、意向工作城市）
 */
public record ResumeContentResponse(
        List<SkillResponse> skills,
        List<EducationRequest> educations,
        List<CareerRequest> careers,
        List<ProjectExperienceRequest> projects,
        List<AwardRequest> awards,
        ResumePersonalInfoDTO personalInfo) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public ResumeContentResponse {
        skills = skills == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(skills));
        educations = educations == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(educations));
        careers = careers == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(careers));
        projects = projects == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(projects));
        awards = awards == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(awards));
    }
}
