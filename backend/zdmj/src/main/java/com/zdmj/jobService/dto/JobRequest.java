package com.zdmj.jobService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

/**
 * 创建/更新岗位请求
 *
 * @param id 岗位ID（更新时不能为空）
 * @param jobName 岗位名称（创建、更新时不能为空）
 * @param companyName 公司名称（创建、更新时不能为空）
 * @param companySize 公司人员规模（可选），未传时为空
 * @param companyFundingType 公司融资阶段（可选）
 * @param companyIndustries 公司所属行业（可选）
 * @param companyIntroduction 公司介绍（可选）
 * @param description 岗位描述，包含工作内容与岗位要求（创建、更新时不能为空）
 * @param location 工作地点（创建、更新时不能为空）
 * @param salaryMin 最低薪资（创建、更新时不能为空）
 * @param salaryMax 最高薪资（创建、更新时不能为空）
 * @param salaryType 薪资类型（创建、更新时不能为空）：1=日薪 / 2=月薪 / 3=年薪
 * @param link 岗位链接（选填，未传时写入空字符串）
 * @param keywords 岗位关键词（字符串列表，写入 jobs.keywords）
 */
public record JobRequest(
        @NotNull(message = "岗位ID不能为空", groups = UpdateGroup.class)
        Long id,
        @NotBlank(message = "岗位名称不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        String jobName,
        @NotBlank(message = "公司名称不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        String companyName,
        Integer companySize,
        Integer companyFundingType,
        List<String> companyIndustries,
        String companyIntroduction,
        @NotBlank(message = "岗位描述不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        String description,
        @NotBlank(message = "工作地点不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        String location,
        @NotNull(message = "最低薪资不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        Integer salaryMin,
        @NotNull(message = "最高薪资不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        Integer salaryMax,
        @NotNull(message = "薪资类型不能为空", groups = { CreateGroup.class, UpdateGroup.class })
        @Min(value = 1, message = "薪资类型最小值为1", groups = { CreateGroup.class, UpdateGroup.class })
        @Max(value = 3, message = "薪资类型最大值为3", groups = { CreateGroup.class, UpdateGroup.class })
        Integer salaryType,
        String link,
        List<String> keywords) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public JobRequest {
        companyIndustries = companyIndustries == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(companyIndustries));
        keywords = keywords == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(keywords));
    }
}
