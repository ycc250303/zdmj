package com.zdmj.jobService.enums;

/**
 * 岗位分页用工类型；与 {@code jobs.salary_type} 对应。
 * <p>API 查询参数 {@code employment} 须严格传枚举名：{@link #INTERN}、{@link #FULL_TIME}。</p>
 * <ul>
 *   <li>{@link #INTERN} → {@code salary_type = 1}（日薪/实习）</li>
 *   <li>{@link #FULL_TIME} → {@code salary_type IN (2, 3)}（月薪+年薪/全职）</li>
 * </ul>
 */
public enum JobEmploymentEnum {

    /**
     * 实习（日薪，salary_type = 1）
     */
    INTERN(1),

    /**
     * 全职（月薪+年薪；查询层映射为 salary_type IN (2, 3)）
     */
    FULL_TIME(2);

    private final int salaryType;

    JobEmploymentEnum(int salaryType) {
        this.salaryType = salaryType;
    }

    public int getSalaryType() {
        return salaryType;
    }

    /**
     * 解析查询参数；须严格匹配枚举名（如 {@code FULL_TIME}、{@code INTERN}）。
     */
    public static JobEmploymentEnum parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
