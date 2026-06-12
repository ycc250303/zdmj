package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历导入结构化识别结果（字段对齐 educations / careers / project_experiences / skills / users 表，不含 id）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportParseResultDTO {

    private PersonalInfo personalInfo;

    private List<EducationItem> educations = new ArrayList<>();

    private List<CareerItem> careers = new ArrayList<>();

    private List<ProjectItem> projects = new ArrayList<>();

    private SkillItem skill;

    private List<String> warnings = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersonalInfo {
        private String name;
        private String phone;
        private String email;
        /** 前端展示用，users 表无独立 major 列 */
        private String major;
        @JsonAlias({ "homepageUrl", "website", "homepage" })
        private String homepageUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EducationItem {
        private String school;
        private String major;
        /** 1 博士 … 6 其他 */
        private Integer degree;
        private String startDate;
        private String endDate;
        private Boolean visible;
        private String gpa;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CareerItem {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private Boolean visible;
        private String details;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectItem {
        private String name;
        private String role;
        private String startDate;
        private String endDate;
        private String description;
        private String contribution;
        private List<String> techStack;
        /** 模型可返回字符串或字符串数组，归一化后统一为 JSON 数组字符串 */
        private Object highlights;
        private String url;
        private Boolean visible;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillItem {
        private String name;
        private List<SkillItemDTO> content = new ArrayList<>();
    }
}
