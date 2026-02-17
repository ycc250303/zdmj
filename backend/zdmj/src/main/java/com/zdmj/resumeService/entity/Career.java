package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/**
 * 工作/实习经历实体类
 * 对应数据库表：careers
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("careers")
public class Career extends BaseEntity {
    /**
     * 工作/实习经历ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 公司名称
     */
    private String company;

    /**
     * 职位名称
     */
    private String position;

    /**
     * 入职时间
     */
    private LocalDate startDate;

    /**
     * 离职时间（在职可为空）
     */
    private LocalDate endDate;

    /**
     * 是否在简历中展示
     */
    private Boolean visible;

    /**
     * 工作职责/业绩（可富文本）
     */
    private String details;
}
