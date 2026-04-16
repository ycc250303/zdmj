package com.zdmj.jobService.enums;

/**
 * 岗位分页：实习 / 全职 与 {@code jobs.salary_type} 对应关系。
 * <p>日薪({@link #INTERN})=实习，月薪({@link #FULL_TIME})=全职；年薪不参与本筛选。</p>
 */
public enum JobEmploymentFilter {

    /**
     * 实习（日薪，salary_type = 1）
     */
    INTERN(1),

    /**
     * 全职（月薪，salary_type = 2）
     */
    FULL_TIME(2);

    private final int salaryType;

    JobEmploymentFilter(int salaryType) {
        this.salaryType = salaryType;
    }

    public int getSalaryType() {
        return salaryType;
    }
}
