package com.zdmj.jobService.dto;

import com.zdmj.jobService.api.JobRole;

/**
 * 岗位方向的关键词评分结果。
 * @param role 识别的岗位方向
 * @param score 命中的关键词数量
 */
public record JobRoleScore(JobRole role, int score) {
}
