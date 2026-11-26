package com.zdmj.userAuthService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * 用户信息更新请求
 *
 * @param name 用户姓名（最大长度50 个字符）
 * @param phone 电话（最大长度 20 个字符）
 * @param website 主页链接（最大长度 500 个字符）
 * @param preferredWorkCity 意向工作城市（最大长度 255 个字符）
 */
public record UserUpdateRequest(
        @Size(max = 50, message = "姓名长度不能超过50个字符")
        String name,
        @Size(max = 20, message = "电话长度不能超过20个字符")
        String phone,
        @Size(max = 500, message = "主页链接长度不能超过500个字符")
        @JsonAlias({ "homepageUrl", "homepage" })
        String website,
        @Size(max = 255, message = "意向工作城市长度不能超过255个字符")
        String preferredWorkCity) {
}
