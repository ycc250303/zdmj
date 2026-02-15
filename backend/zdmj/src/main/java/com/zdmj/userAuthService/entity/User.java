package com.zdmj.userAuthService.entity;

import com.zdmj.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体类
 * 对应数据库表：users
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    /**
     * 用户ID（主键）
     */
    private Long id;

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码（加密后）
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;
}
