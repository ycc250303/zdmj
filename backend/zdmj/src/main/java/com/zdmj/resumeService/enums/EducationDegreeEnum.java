package com.zdmj.resumeService.enums;

import java.util.Arrays;

/**
 * 学历层次枚举
 */
public enum EducationDegreeEnum {
    DOCTOR(1, "博士"),
    MASTER(2, "硕士"),
    BACHELOR(3, "本科"),
    JUNIOR_COLLEGE(4, "大专"),
    HIGH_SCHOOL(5, "高中"),
    OTHER(6, "其他");

    private final int code;
    private final String label;

    EducationDegreeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static EducationDegreeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
