package com.zdmj.matchService.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 当前用户人岗匹配记录列表项（分页出参）。
 *
 * <p>不含四维明细等详情字段；详情仍走 {@link JobStudentMatchResponse}。</p>
 *
 * @param id 匹配记录 ID
 * @param jobId 岗位 ID
 * @param jobName 岗位名称（来自 jobs）
 * @param companyName 公司名称（来自 jobs.company_name）
 * @param overallScore 综合匹配度 0~100
 * @param keySkillMatchRate 关键技能匹配率 0~1
 * @param summary 一句话总结
 * @param updatedAt 最近匹配时间（覆盖写时的 updated_at）
 */
public record JobStudentMatchListItemResponse(
        Long id,
        Long jobId,
        String jobName,
        String companyName,
        Integer overallScore,
        BigDecimal keySkillMatchRate,
        String summary,
        LocalDateTime updatedAt) {
}
