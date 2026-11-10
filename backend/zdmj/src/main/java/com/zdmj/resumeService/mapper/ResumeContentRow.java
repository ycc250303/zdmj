package com.zdmj.resumeService.mapper;

import lombok.Data;

/**
 * 一次查询得到的整份简历。经历字段是 JSON 数组文本，键名与整份内容响应一致。
 */
@Data
public class ResumeContentRow {

    /**
     * 技能 JSON 数组。没有技能时为 {@code []}。
     */
    private String skills;

    /**
     * 姓名，对应 {@code users.name}。
     */
    private String personalName;

    /**
     * 电话，对应 {@code users.phone}。
     */
    private String personalPhone;

    /**
     * 个人主页，对应 {@code users.website}。
     */
    private String personalWebsite;

    /**
     * 意向工作城市，对应 {@code users.preferred_work_city}。
     */
    private String preferredWorkCity;

    /**
     * 教育经历 JSON 数组。没有经历时为 {@code []}。
     */
    private String educations;

    /**
     * 工作经历 JSON 数组。没有经历时为 {@code []}。
     */
    private String careers;

    /**
     * 项目经历 JSON 数组。没有经历时为 {@code []}。
     */
    private String projects;

    /**
     * 获奖信息 JSON 数组。没有获奖时为 {@code []}。
     */
    private String awards;
}
