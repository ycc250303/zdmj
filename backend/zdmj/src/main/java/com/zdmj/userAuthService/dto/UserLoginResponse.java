package com.zdmj.userAuthService.dto;

import lombok.Data;

/**
 * 用户登录响应（包含Token和用户信息）
 */
@Data
public class UserLoginResponse {

    /**
     * JWT Token
     */
    @lombok.ToString.Exclude
    private String token;

    /**
     * 用户信息
     */
    private UserResponse user;
}
