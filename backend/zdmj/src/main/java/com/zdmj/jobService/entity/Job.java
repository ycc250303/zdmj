package com.zdmj.jobService.entity;

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
 * 岗位表 jobs
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "jobs", autoResultMap = true)
public class Job extends BaseEntity {

    /**
     * 岗位ID
     */     
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 岗位名称
     */
    private String jobName;

    /**
     * 公司ID
     */
    private Long companyId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 岗位描述
     */
    private String description;

    /**
     * 工作地点
     */
    private String location;

    /**
     * 最低薪资（元），与 salary_max、salary_type 共同表示薪资范围
     */
    private Integer salaryMin;

    /**
     * 最高薪资（元）
     */
    private Integer salaryMax;

    /**
     * 薪资类型：1=日薪 / 2=月薪 / 3=年薪
     */
    private Integer salaryType;

    /**
     * 岗位职责（JSONB 字符串数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> content;

    /**
     * 岗位要求（JSONB 字符串数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> requirements;

    /**
     * 岗位关键词（JSONB 字符串数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keywords;

    /**
     * 岗位链接
     */
    private String link;
}
