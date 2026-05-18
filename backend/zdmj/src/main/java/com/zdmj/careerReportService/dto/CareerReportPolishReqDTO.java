package com.zdmj.careerReportService.dto;

import lombok.Data;

/**
 * 报告润色请求 DTO
 */
@Data
public class CareerReportPolishReqDTO {

    /**
     * 润色要求（如“更强调可执行性/更简洁”）。
     */
    private String instruction;
}
