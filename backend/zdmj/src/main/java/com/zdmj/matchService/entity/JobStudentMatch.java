package com.zdmj.matchService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbStringTypeHandler;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 人岗匹配分析实体类
 * 对应数据库表：job_student_matches
 *
 * <p>每一行表示「某用户 × 某岗位」一次匹配分析结果。落库时按 (user_id, job_id) 唯一约束 upsert，
 * 重新生成会覆盖旧记录，避免历史脏数据。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "job_student_matches", autoResultMap = true)
public class JobStudentMatch extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生用户ID（逻辑外键：users.id）
     */
    private Long userId;

    /**
     * 岗位ID（逻辑外键：jobs.id）
     */
    private Long jobId;

    /**
     * 综合匹配度 0~100（按 weights 对四维加权得到）
     */
    private Integer overallScore;

    /**
     * 基础要求维度评分（0~100）
     */
    private Integer basicScore;

    /**
     * 职业技能维度评分（0~100）
     */
    private Integer professionalSkillScore;

    /**
     * 职业素养维度评分（0~100）
     */
    private Integer professionalQualityScore;

    /**
     * 发展潜力维度评分（0~100）
     */
    private Integer developmentPotentialScore;

    /**
     * 维度权重快照（JSONB），形如：
     * <pre>{"basic":0.20,"professionalSkill":0.40,"professionalQuality":0.15,"developmentPotential":0.25}</pre>
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String weights;

    /**
     * 各维度对比明细（JSONB）：每个 key 对应一个 {@link com.zdmj.matchService.dto.DimensionMatchResponse}。
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String dimensionDetail;

    /**
     * 命中亮点（JSONB 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> matchedHighlights;

    /**
     * 关键差距（JSONB 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> criticalGaps;

    /**
     * 命中的岗位关键词（JSONB 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> matchedKeywords;

    /**
     * 缺失的岗位关键词（JSONB 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> missingKeywords;

    /**
     * 关键技能匹配率（0~1，命中关键词数 / 岗位关键词数）。
     */
    private BigDecimal keySkillMatchRate;

    /**
     * 一句话总结
     */
    private String summary;

    /**
     * 岗位类型展示值（如 java-backend / frontend / default）
     */
    private String targetRoleType;

    /**
     * 实际使用的提示词名称（如 job-student-match/java-backend）
     */
    private String promptName;
}
