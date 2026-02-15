package com.zdmj.userAuthService.dto;

import lombok.Data;

/**
 * 用户信息DTO（响应用，不包含敏感信息）
 */
@Data
public class UserDTO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;
}
