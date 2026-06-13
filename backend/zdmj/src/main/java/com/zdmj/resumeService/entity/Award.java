package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 获奖信息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("awards")
public class Award extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 1=奖学金, 2=竞赛获奖, 3=其他类型 */
    private Integer awardType;

    private String name;

    @TableField("award_date")
    private LocalDate awardDate;

    private String description;
}
