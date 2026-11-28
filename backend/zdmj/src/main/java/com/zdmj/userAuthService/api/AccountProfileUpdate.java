package com.zdmj.userAuthService.api;

/**
 * 账户资料同步字段，null 保留原值。
 * @param name 姓名，对应 users.name，最多50个字符
 * @param phone 电话，对应 users.phone，最多20个字符
 * @param website 主页，对应 users.website，最多500个字符
 * @param preferredWorkCity 意向城市，对应 users.preferred_work_city，最多255个字符
 */
public record AccountProfileUpdate(String name, String phone, String website, String preferredWorkCity) {
}
