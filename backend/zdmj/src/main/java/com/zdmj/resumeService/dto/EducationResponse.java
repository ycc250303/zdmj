package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.resumeService.enums.EducationDegreeEnum;
import lombok.Data;

import java.time.LocalDate;

/**
 * 教育经历响应（镜像当前 Education Entity JSON，含 degreeEnum）
 */
@Data
public class EducationResponse {

    private Long id;

    private Long userId;

    private String school;

    private String major;

    private Integer degree;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String gpa;

    public EducationDegreeEnum getDegreeEnum() {
        return EducationDegreeEnum.fromCode(this.degree);
    }

    public void setDegreeEnum(EducationDegreeEnum degreeEnum) {
        this.degree = degreeEnum != null ? degreeEnum.getCode() : null;
    }
}
