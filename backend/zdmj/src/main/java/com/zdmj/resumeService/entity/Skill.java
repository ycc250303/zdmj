package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能实体类
 * 对应数据库表：skills
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skills")
public class Skill extends BaseEntity {
    /**
     * 技能ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 技能清单名称
     */
    private String name;

    /**
     * 职业技能描述（JSONB格式）
     * 示例：[{"type": "前端框架", "content": ["React", "Vue.js"]}, ...]
     */
    private String content;
}
