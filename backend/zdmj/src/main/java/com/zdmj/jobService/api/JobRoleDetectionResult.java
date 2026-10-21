package com.zdmj.jobService.api;

/**
 * 岗位方向识别结果。
 * @param role 岗位方向，对应 target_role_type 的规范代码
 * @param confidence 识别置信度，范围为 [0, 1]
 * @param reason 识别依据
 */
public record JobRoleDetectionResult(JobRole role, double confidence, String reason) {
    /**
     * 校验识别结果的完整性。
     * @throws IllegalArgumentException 方向为空、置信度非法或识别依据为空白
     */
    public JobRoleDetectionResult {
        if (role == null) {
            throw new IllegalArgumentException("岗位方向不能为空");
        }
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("识别置信度必须为 [0, 1] 内的有限数值");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("识别依据不能为空白");
        }
    }
}
