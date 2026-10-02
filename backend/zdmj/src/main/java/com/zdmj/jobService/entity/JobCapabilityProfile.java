package com.zdmj.jobService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位能力画像实体类
 * 对应数据库表：job_capability_profiles
 *
 * <p>一行对应一个岗位。{@code userId} 只保留在生成过程中，表 {@code job_capability_profiles} 没有该列。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "job_capability_profiles", autoResultMap = true)
public class JobCapabilityProfile extends BaseEntity {
    /**
     * 画像ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 生成该画像时的登录用户。不写入 {@code job_capability_profiles}。
     */
    @TableField(exist = false)
    private Long userId;

    /**
     * 岗位ID
     */
    private Long jobId;

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
     * 岗位已写明的核心要求亮点（JSONB）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> strengths;

    /**
     * 补充要求：JD 未写明的该方向常见核心门槛（JSONB，列名 missing_skills）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> missingSkills;

    /**
     * 摘要
     */
    private String summary;

}
