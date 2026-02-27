package com.zdmj.common.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 基础实体类
 * 包含通用字段：创建时间、更新时间
 */
@Data
public abstract class BaseEntity {

    /**
     * 创建时间
     */
    @JsonIgnore
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonIgnore
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
