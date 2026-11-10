package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能。一行对应一项技能，归属 {@code skills.user_id}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skills")
public class Skill extends BaseEntity {

    /**
     * 技能编号，对应 {@code skills.id}。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户编号，对应 {@code skills.user_id}。
     */
    private Long userId;

    /**
     * 技能名称，对应 {@code skills.name}，不能为空，最长 255。
     */
    private String name;

    /**
     * 技能说明，对应 {@code skills.description}，可空，最长 500。空白保存为 null。
     */
    private String description;
}
