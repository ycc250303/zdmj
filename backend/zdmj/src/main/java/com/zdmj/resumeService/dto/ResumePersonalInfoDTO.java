package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 简历基本信息（对应 users 表姓名、电话、主页与意向工作城市）。
 */
@Data
public class ResumePersonalInfoDTO {

    @Size(max = 100, message = "姓名长度不能超过100个字符")
    private String name;

    @Size(max = 50, message = "电话长度不能超过50个字符")
    private String phone;

    @Size(max = 500, message = "主页链接长度不能超过500个字符")
    @JsonAlias({ "website", "homepage" })
    private String homepageUrl;

    @Size(max = 255, message = "意向工作城市长度不能超过255个字符")
    private String preferredWorkCity;
}
