package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生就业能力画像实体类
 * 对应数据库表：student_capability_profiles
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_capability_profiles")
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
}
