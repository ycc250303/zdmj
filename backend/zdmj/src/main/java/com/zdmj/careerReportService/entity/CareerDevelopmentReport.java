package com.zdmj.careerReportService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 职业发展报告实体类
 * 对应数据库表：career_development_reports
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "career_development_reports", autoResultMap = true)
public class CareerDevelopmentReport extends BaseEntity {

    /**
     * 报告ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（逻辑外键：users.id）
     */
    private Long userId;

    /**
     * 岗位ID（逻辑外键：jobs.id）
     */
    private Long jobId;

    /**
     * 人岗匹配记录ID（逻辑外键：job_student_matches.id）
     */
    private Long matchId;

    /**
     * 岗位职业图谱ID（逻辑外键：job_career_graphs.id）
     */
    private Long careerGraphId;

    /**
     * 生成时的学生能力画像快照（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String studentProfileSnapshot;

    /**
     * 生成时的岗位能力画像快照（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String jobProfileSnapshot;

    /**
     * 生成时的人岗匹配结果快照（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String matchSnapshot;

    /**
     * RAG 命中的知识来源列表（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String knowledgeSources;

    /**
     * 报告结构化正文（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String reportContent;

    /**
     * 完整性检查与质量标记（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String qualityFlags;

    /**
     * 状态：1=草稿/2=已校验/3=已发布/4=校验未通过
     */
    private Integer status;

    /**
     * 完整度评分（0~100）
     */
    private Integer completenessScore;

    /**
     * 同一用户+岗位下的版本号
     */
    private Integer version;

    /**
     * 是否为该用户+岗位下的最新版本
     */
    private Boolean isLatest;

    /**
     * 实际使用的提示词名称
     */
    private String promptName;
}
