package com.zdmj.jobService.dto;

/**
 * 岗位方向识别的模型输出，由岗位业务检查有效性后转换为公开识别结果。
 * @param roleCode 方向代码，允许 unknown，由业务校验支持的代码
 * @param confidence 置信度，业务要求为零到一的有限数值
 * @param reason 模型提供的识别依据，允许缺省
 */
public record JobRoleModelResponse(String roleCode, Double confidence, String reason) {
}
