package com.zdmj.jobService.dto;

import lombok.Data;

import java.util.List;

/**
 * 岗位创建/更新响应（镜像当前 Job Entity JSON）
 */
@Data
public class JobResponse {

    private Long id;

    private String jobName;

    private Long companyId;

    private String companyName;

    /**
     * 岗位描述，包含工作内容与岗位要求
     */
    private String description;

    private String location;

    private Integer salaryMin;

    private Integer salaryMax;

    private Integer salaryType;

    private List<String> keywords;

    private String link;
}
