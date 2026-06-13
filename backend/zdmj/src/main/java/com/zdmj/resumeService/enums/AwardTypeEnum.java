package com.zdmj.resumeService.enums;

import java.util.Arrays;

/**
 * 奖项类型枚举
 */
public enum AwardTypeEnum {
    SCHOLARSHIP(1, "奖学金"),
    COMPETITION(2, "竞赛获奖"),
    OTHER(3, "其他类型");

    private final int code;
    private final String label;

    AwardTypeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AwardTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
