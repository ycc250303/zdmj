package com.zdmj.jobService.dto;

import java.util.List;

import com.zdmj.jobService.enums.JobEmploymentFilter;

import lombok.Data;

/**
 * 岗位分页查询条件（对应 GET /jobs 的查询参数）。
 */
@Data
public class JobPageQueryDTO {

    /**
     * 页码，默认 1；当 page <= 0 时按 1 处理
     */
    private Integer page;

    /**
     * 每页条数，默认 20；最大 100，超过上限按 100 处理
     */
    private Integer limit;

    /**
     * 公司规模（多选）
     */
    private List<Integer> companySizes;

    /**
     * 公司融资阶段（多选）
     */
    private List<Integer> fundingTypes;

    /**
     * 行业（多选）
     */
    private List<String> industries;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 实习 / 全职；不传表示不限制
     */
    private JobEmploymentFilter employment;

    /**
     * 期望薪资下限（元）
     */
    private Integer filterSalaryMin;

    /**
     * 期望薪资上限（元）
     */
    private Integer filterSalaryMax;

    /**
     * 岗位名称关键词（对 job_name 做包含匹配）
     */
    private String jobName;
}
