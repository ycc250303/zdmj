package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * 简历基本信息（对应 users 表姓名、电话、主页与意向工作城市）。
 *
 * @param name 姓名，最长 100 个字符
 * @param phone 联系电话，最长 50 个字符
 * @param homepageUrl 个人主页，最长 500 个字符
 * @param preferredWorkCity 意向工作城市，最长 255 个字符
 */
public record ResumePersonalInfoDTO(
        @Size(max = 100, message = "姓名长度不能超过100个字符")
        String name,
        @Size(max = 50, message = "电话长度不能超过50个字符")
        String phone,
        @Size(max = 500, message = "主页链接长度不能超过500个字符")
        @JsonAlias({ "website", "homepage" })
        String homepageUrl,
        @Size(max = 255, message = "意向工作城市长度不能超过255个字符")
        String preferredWorkCity) {
}
