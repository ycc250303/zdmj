package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 简历实体类
 * 对应数据库表：resumes
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "resumes", autoResultMap = true)
public class Resume extends BaseEntity {
    /**
     * 简历ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 技能清单ID（关联skills表）
     */
    private Long skillId;

    /**
     * 项目经历ID数组（JSONB数组，存储project_experiences ID）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> projects;

    /**
     * 工作经历ID数组（JSONB数组，存储career ID）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> careers;

    /**
     * 教育经历ID数组（JSONB数组，存储education ID）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> educations;

    /**
     * 获奖信息ID数组（JSONB数组，存储 awards ID）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> awards;

    /**
     * 专用简历ID数组（JSONB数组，存储resume_matches ID）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> resumeMatchedIds;
}
