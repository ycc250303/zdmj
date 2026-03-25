package com.zdmj.jobService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位详情（含公司摘要，用于接口与 Redis 缓存）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailDTO {

    private Long id;
    private String jobName;
    private Long companyId;
    private String companyName;
    private String description;
    private String location;
    private String salary;
    private String link;
    private String content;
    private String requirements;
    private String recall;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> companyIndustries;
    private Integer companySize;
    private Integer companyFundingType;
}
