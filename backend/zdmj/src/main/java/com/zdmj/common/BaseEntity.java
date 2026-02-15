package com.zdmj.common;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础实体类
 * 包含通用字段：创建时间、更新时间
 */
@Data
public abstract class BaseEntity {

    /**
     * 创建时间
     */
    private LocalDateTime createAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateAt;
}
