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

    private String description;

    private String location;

    private Integer salaryMin;

    private Integer salaryMax;

    private Integer salaryType;

    private List<String> content;

    private List<String> requirements;

    private List<String> keywords;

    private String link;
}
