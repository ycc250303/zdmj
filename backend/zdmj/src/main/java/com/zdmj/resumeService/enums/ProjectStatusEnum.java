package com.zdmj.resumeService.enums;

import java.util.Arrays;

/**
 * 项目分析状态枚举
 */
public enum ProjectStatusEnum {
    COMMITTED(1, "committed"),
    MINING(2, "mining"),
    POLISHING(3, "polishing"),
    COMPLETED(4, "completed");

    private final int code;
    private final String label;

    ProjectStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ProjectStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
