package com.zdmj.jobService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位分页列表项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobListItemDTO {

    private Long id;
    private String jobName;
    private Long companyId;
    private String companyName;
    private String description;
    private String location;
    private String salary;
    private String link;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> companyIndustries;
    private Integer companySize;
    private Integer companyFundingType;
}
