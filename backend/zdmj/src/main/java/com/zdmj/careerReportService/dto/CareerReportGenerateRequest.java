package com.zdmj.careerReportService.dto;

import lombok.Data;

/**
 * 生成职业发展报告请求体
 */
@Data
public class CareerReportGenerateRequest {

    /**
     * 可选：用户额外目标偏好（城市/行业/岗位倾向等）。
     */
    private String userPreference;

    /**
     * 可选：生成侧重点（如「补齐项目经历」「强化算法方向」）。
     */
    private String focus;
}
