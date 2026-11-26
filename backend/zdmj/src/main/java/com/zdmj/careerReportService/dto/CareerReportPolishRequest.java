package com.zdmj.careerReportService.dto;

/**
 * 报告润色请求体
 *
 * @param instruction 润色要求（如「更强调可执行性/更简洁」）。
 */
public record CareerReportPolishRequest(
        String instruction) {
}
