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

    /**
     * 用户 ID，对应 {@code awards.user_id}。
     */
    private Long userId;

    /**
     * 奖项名称，对应 {@code awards.name}。奖学金、竞赛和其他荣誉都写在名称中。
     */
    private String name;

    /**
     * 获奖日期，对应 {@code awards.award_date}，不能为空。
     */
    @TableField("award_date")
    private LocalDate awardDate;

    /**
     * 奖项说明，对应 {@code awards.description}，可空。
     */
    private String description;
}
