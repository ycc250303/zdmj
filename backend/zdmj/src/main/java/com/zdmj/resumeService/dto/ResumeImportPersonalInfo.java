package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 简历导入识别的基本信息。
 *
 * @param name 姓名
 * @param phone 联系电话
 * @param email 电子邮箱
 * @param major 前端展示用，users 表无独立 major 列
 * @param homepageUrl 个人主页
 * @param preferredWorkCity 意向工作城市
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeImportPersonalInfo(
        String name,
        String phone,
        String email,
        String major,
        @JsonAlias({ "homepageUrl", "website", "homepage" })
        String homepageUrl,
        @JsonAlias({ "preferredWorkCity", "targetCity", "expectedCity" })
        String preferredWorkCity) {
}
