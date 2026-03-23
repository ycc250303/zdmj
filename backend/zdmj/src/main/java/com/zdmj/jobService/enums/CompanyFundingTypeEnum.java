package com.zdmj.jobService.enums;

import java.util.Arrays;

/**
 * 公司融资阶段枚举
 */
public enum CompanyFundingTypeEnum {
    A_ROUND(1, "A轮"),
    B_ROUND(2, "B轮"),
    C_ROUND(3, "C轮"),
    D_PLUS_ROUND(4, "D轮及以上"),
    NO_NEED_FINANCING(5, "不需要融资"),
    ANGEL_ROUND(6, "天使轮"),
    LISTED(7, "已上市"),
    UNFUNDED(8, "未融资");

    private final int code;
    private final String label;

    CompanyFundingTypeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CompanyFundingTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
