package com.zdmj.jobService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbListStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位表 jobs（不包含 embedding 列，由 Python 等流程维护向量）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jobs")
public class Job extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private Long companyId;

    private String companyName;

    private String description;

    private String location;

    private String salary;

    private String link;

    private String content;

    private String requirements;

    @TableField(typeHandler = JsonbListStringTypeHandler.class)
    private String recall;
}
