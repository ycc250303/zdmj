package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.resumeService.enums.EducationDegreeEnum;

import java.time.LocalDate;

/**
 * 教育经历响应（镜像当前 Education Entity JSON，含 degreeEnum）
 *
 * @param id 教育经历编号，对应 educations.id
 * @param userId 所属用户编号
 * @param school 学校名称
 * @param major 专业名称
 * @param degree 学历，对应 degree：1 博士、2 硕士、3 本科、4 大专、5 高中、6 其他
 * @param startDate 入学日期，格式为 yyyy-MM-dd
 * @param endDate 毕业日期，格式为 yyyy-MM-dd；在读时可为空
 * @param gpa 平均绩点
 */
public record EducationResponse(
        Long id,
        Long userId,
        String school,
        String major,
        Integer degree,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate endDate,
        String gpa) {

    public EducationDegreeEnum getDegreeEnum() {
        return EducationDegreeEnum.fromCode(this.degree);
    }
}
