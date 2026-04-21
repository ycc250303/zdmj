package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbStringTypeHandler;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生就业能力画像实体类
 * 对应数据库表：student_capability_profiles
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "student_capability_profiles", autoResultMap = true)
public class StudentCapabilityProfile extends BaseEntity {
    /**
     * 画像ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 专业技能
     */
    private String professionalSkills;

    /**
     * 证书
     */
    private String certificates;

    /**
     * 创新能力
     */
    private String innovationAbility;

    /**
     * 学习能力
     */
    private String learningAbility;

    /**
     * 抗压能力
     */
    private String pressureResistance;

    /**
     * 沟通能力
     */
    private String communicationAbility;

    /**
     * 实习能力
     */
    private String practicalAbility;

    /**
     * 完整度评分 (0-100)
     */
    private Integer completenessScore;

    /**
     * 竞争力评分 (0-100)
     */
    private Integer competitivenessScore;

    /**
     * 岗位专项评估总分（可选）
     */
    private Integer overallScore;

    /**
     * 岗位识别置信度（0~1）
     */
    private BigDecimal roleConfidence;

    /**
     * 实际使用的提示词名称
     */
    private String promptName;

    /**
     * 岗位类型展示值（如 software-test）
     */
    private String targetRoleType;

    /**
     * 分项评分明细（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String scoreDetail;

    /**
     * 缺失技能项（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String missingSkills;

    /**
     * 证据不足项（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String weakEvidenceItems;

    /**
     * 改进建议（JSONB）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String suggestions;
}
