package com.zdmj.userAuthService.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求
 *
 * @param usernameOrEmail 用户名或邮箱（不能为空）
 * @param password 密码（不能为空）
 */
public record UserLoginRequest(
        @NotBlank(message = "用户名或邮箱不能为空")
        String usernameOrEmail,
        @NotBlank(message = "密码不能为空")
        String password) {

    @Override
    public String toString() {
        return "UserLoginRequest[usernameOrEmail=" + usernameOrEmail + "]";
    }
}
