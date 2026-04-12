package com.zdmj.jobService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 岗位分页列表项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobListItemDTO {

    /**
     * 岗位ID
     */
    private Long id;
    /**
     * 岗位名称
     */
    private String jobName;
    /**
     * 公司ID
     */
    private Long companyId;
    /**
     * 公司名称
     */
    private String companyName;
    /**
     * 岗位描述
     */
    private String description;
    /**
     * 工作地点
     */
    private String location;
    /**
     * 最低薪资（元）
     */
    private Integer salaryMin;
    /**
     * 最高薪资（元）
     */
    private Integer salaryMax;
    /**
     * 薪资类型：1=日薪 / 2=月薪 / 3=年薪
     */
    private Integer salaryType;
    /**
     * 薪资展示文案
     */
    private String salary;
    /**
     * 岗位链接
     */
    private String link;
    /**
     * 岗位职责（对应 jobs.content）
     */
    private List<String> jobDuties;
    /**
     * 岗位要求（对应 jobs.requirements）
     */
    private List<String> jobRequirements;
    /**
     * 岗位关键词（对应 jobs.keywords）
     */
    private List<String> keywords;
    /**
     * 公司所属行业
     */
    private List<String> companyIndustries;
    /**
     * 公司人员规模
     */
    private Integer companySize;
    /**
     * 公司融资阶段
     */
    private Integer companyFundingType;
}
