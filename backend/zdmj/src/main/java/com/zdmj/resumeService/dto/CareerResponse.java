package com.zdmj.resumeService.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 工作/实习经历响应（镜像当前 Career Entity JSON）
 */
@Data
public class CareerResponse {

    private Long id;

    private Long userId;

    private String company;

    private String position;

    private LocalDate startDate;

    private LocalDate endDate;

    private String details;
}
