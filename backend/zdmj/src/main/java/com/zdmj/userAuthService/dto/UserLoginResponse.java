package com.zdmj.userAuthService.dto;

/**
 * 用户登录响应（包含Token和用户信息）
 *
 * @param token JWT Token
 * @param user 用户信息
 */
public record UserLoginResponse(
        String token,
        UserResponse user) {

    @Override
    public String toString() {
        return "UserLoginResponse[user=" + user + "]";
    }
}
