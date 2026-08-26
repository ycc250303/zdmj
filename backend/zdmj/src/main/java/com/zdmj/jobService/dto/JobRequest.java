package com.zdmj.jobService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

/**
 * 创建/更新岗位请求
 */
@Data
public class JobRequest {

    /**
     * 岗位ID（更新时不能为空）
     */
    @NotNull(message = "岗位ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 岗位名称（创建、更新时不能为空）
     */
    @NotBlank(message = "岗位名称不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String jobName;

    /**
     * 公司名称（创建、更新时不能为空）
     */
    @NotBlank(message = "公司名称不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String companyName;

    /**
     * 公司人员规模（可选），未传时为空
     */
    private Integer companySize;

    /**
     * 公司融资阶段（可选）
     */
    private Integer companyFundingType;

    /**
     * 公司所属行业（可选）
     */
    private List<String> companyIndustries;

    /**
     * 公司介绍（可选）
     */
    private String companyIntroduction;

    /**
     * 岗位描述（创建、更新时不能为空）
     */
    @NotBlank(message = "岗位描述不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String description;

    /**
     * 工作地点（创建、更新时不能为空）
     */
    @NotBlank(message = "工作地点不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String location;

    /**
     * 最低薪资（创建、更新时不能为空）
     */
    @NotNull(message = "最低薪资不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private Integer salaryMin;

    /**
     * 最高薪资（创建、更新时不能为空）
     */
    @NotNull(message = "最高薪资不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private Integer salaryMax;

    /**
     * 薪资类型（创建、更新时不能为空）：1=日薪 / 2=月薪 / 3=年薪
     */
    @NotNull(message = "薪资类型不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    @Min(value = 1, message = "薪资类型最小值为1", groups = { CreateGroup.class, UpdateGroup.class })
    @Max(value = 3, message = "薪资类型最大值为3", groups = { CreateGroup.class, UpdateGroup.class })
    private Integer salaryType;

    /**
     * 岗位链接（选填，未传时写入空字符串）
     */
    private String link;

    /**
     * 岗位职责（字符串列表，写入 jobs.content）
     */
    private List<String> jobDuties;

    /**
     * 岗位要求（字符串列表，写入 jobs.requirements）
     */
    private List<String> jobRequirements;

    /**
     * 岗位关键词（字符串列表，写入 jobs.keywords）
     */
    private List<String> keywords;
}
