package com.zdmj.jobService.dto;

import java.util.List;

/**
 * 岗位创建/更新响应（镜像当前 Job Entity JSON）
 *
 * @param id 岗位编号，对应 jobs.id
 * @param jobName 岗位名称
 * @param companyId 公司编号
 * @param companyName 公司名称
 * @param description 岗位描述，包含工作内容与岗位要求
 * @param location 工作地点
 * @param salaryMin 最低薪资（元），对应 jobs.salary_min
 * @param salaryMax 最高薪资（元），对应 jobs.salary_max
 * @param salaryType 薪资类型，对应 jobs.salary_type：1 日薪、2 月薪、3 年薪
 * @param keywords 岗位关键词
 * @param link 岗位链接，对应 jobs.link
 */
public record JobResponse(
        Long id,
        String jobName,
        Long companyId,
        String companyName,
        String description,
        String location,
        Integer salaryMin,
        Integer salaryMax,
        Integer salaryType,
        List<String> keywords,
        String link) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public JobResponse {
        keywords = keywords == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(keywords));
    }
}
