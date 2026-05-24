package com.zdmj.careerReportService.dto;

import lombok.Data;

/**
 * 报告润色请求体
 */
@Data
public class CareerReportPolishRequest {

    /**
     * 润色要求（如「更强调可执行性/更简洁」）。
     */
    private String instruction;
}
