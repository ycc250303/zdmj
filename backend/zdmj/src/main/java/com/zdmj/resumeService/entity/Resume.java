package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbListTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 简历实体类
 * 对应数据库表：resumes
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resumes")
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
     * 简历名称
     */
    private String name;

    /**
     * 技能清单ID（关联skills表）
     */
    private Long skillId;

    /**
     * 项目经历ID数组（JSONB数组，存储project_experiences ID）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<Long> projects;

    /**
     * 工作经历ID数组（JSONB数组，存储career ID）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<Long> careers;

    /**
     * 教育经历ID数组（JSONB数组，存储education ID）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<Long> educations;

    /**
     * 专用简历ID数组（JSONB数组，存储resume_matches ID）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<Long> resumeMatchedIds;
}
