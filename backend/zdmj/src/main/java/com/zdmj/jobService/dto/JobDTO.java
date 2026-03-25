package com.zdmj.jobService.dto;

import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 创建/更新岗位请求体
 */
@Data
public class JobDTO {

    @NotNull(message = "岗位ID不能为空", groups = UpdateGroup.class)
    private Long id;

    @NotBlank(message = "岗位名称不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String jobName;

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

    @NotBlank(message = "岗位描述不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String description;

    @NotBlank(message = "工作地点不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String location;

    @NotBlank(message = "薪资范围不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String salary;

    @NotBlank(message = "岗位链接不能为空", groups = { CreateGroup.class, UpdateGroup.class })
    private String link;

    private String content;

    private String requirements;
}
