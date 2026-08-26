package com.zdmj.matchService.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 当前用户人岗匹配记录列表项（分页出参）。
 *
 * <p>不含四维明细等详情字段；详情仍走 {@link JobStudentMatchResponse}。</p>
 */
@Data
public class JobStudentMatchListItemResponse {

    /**
     * 匹配记录 ID
     */
    private Long id;

    /**
     * 岗位 ID
     */
    private Long jobId;

    /**
     * 岗位名称（来自 jobs）
     */
    private String jobName;

    /**
     * 公司名称（来自 jobs.company_name）
     */
    private String companyName;

    /**
     * 综合匹配度 0~100
     */
    private Integer overallScore;

    /**
     * 关键技能匹配率 0~1
     */
    private BigDecimal keySkillMatchRate;

    /**
     * 一句话总结
     */
    private String summary;

    /**
     * 最近匹配时间（覆盖写时的 updated_at）
     */
    private LocalDateTime updatedAt;
}
