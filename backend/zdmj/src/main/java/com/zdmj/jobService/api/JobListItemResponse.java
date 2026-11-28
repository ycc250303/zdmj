package com.zdmj.jobService.api;

import java.util.List;

/**
 * 岗位列表/详情响应
 *
 * @param id 岗位ID
 * @param jobName 岗位名称
 * @param companyId 公司ID
 * @param companyName 公司名称
 * @param description 岗位描述，包含工作内容与岗位要求
 * @param location 工作地点
 * @param salaryMin 最低薪资（元）
 * @param salaryMax 最高薪资（元）
 * @param salaryType 薪资类型：1=日薪 / 2=月薪 / 3=年薪
 * @param salary 薪资展示文案
 * @param link 岗位链接
 * @param keywords 岗位关键词（对应 jobs.keywords）
 * @param companyIndustries 公司所属行业
 * @param companySize 公司人员规模
 * @param companyFundingType 公司融资阶段
 */
public record JobListItemResponse(
        Long id,
        String jobName,
        Long companyId,
        String companyName,
        String description,
        String location,
        Integer salaryMin,
        Integer salaryMax,
        Integer salaryType,
        String salary,
        String link,
        List<String> keywords,
        List<String> companyIndustries,
        Integer companySize,
        Integer companyFundingType) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public JobListItemResponse {
        keywords = keywords == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(keywords));
        companyIndustries = companyIndustries == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(companyIndustries));
    }
}
