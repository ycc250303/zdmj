package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/**
 * 教育经历类
 * 对应数据库表：educations
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("educations")
public class Education extends BaseEntity {
    /**
     * 教育经历ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 学校名称
     */
    private String school;

    /**
     * 专业名称
     */
    private String major;

    /**
     * 学历层次
     */
    private Integer degree;

    /**
     * 入学时间
     */
    @TableField("start_date")
    private LocalDate startDate;

    /**
     * 毕业时间
     */
    @TableField("end_date")
    private LocalDate endDate;

    /**
     * 是否在简历中展示
     */
    private Boolean visible;

    /**
     * 绩点
     */
    private String gpa;
}
