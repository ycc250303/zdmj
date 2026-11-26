package com.zdmj.userAuthService.dto;

/**
 * 用户信息响应（不包含敏感信息）
 *
 * @param id 用户ID
 * @param username 用户名
 * @param email 邮箱
 * @param name 用户姓名
 * @param phone 电话
 * @param website 主页链接
 * @param preferredWorkCity 意向工作城市
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String name,
        String phone,
        String website,
        String preferredWorkCity) {
}
