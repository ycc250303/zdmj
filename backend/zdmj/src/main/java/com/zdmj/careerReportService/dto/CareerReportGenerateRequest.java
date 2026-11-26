package com.zdmj.careerReportService.dto;

/**
 * 生成职业发展报告请求体
 *
 * @param userPreference 可选：用户额外目标偏好（城市/行业/岗位倾向等）。
 * @param focus 可选：生成侧重点（如「补齐项目经历」「强化算法方向」）。
 */
public record CareerReportGenerateRequest(
        String userPreference,
        String focus) {
}
