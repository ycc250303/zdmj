package com.zdmj.jobService.enums;

import java.util.Arrays;

/**
 * 公司人员规模枚举
 */
public enum CompanySizeEnum {
    BELOW_20(1, "20人以下"),
    FROM_20_TO_99(2, "20-99人"),
    FROM_100_TO_299(3, "100-299人"),
    FROM_300_TO_499(4, "300-499人"),
    FROM_500_TO_999(5, "500-999人"),
    FROM_1000_TO_9999(6, "1000-9999人"),
    ABOVE_10000(7, "10000人以上");

    private final int code;
    private final String label;

    CompanySizeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CompanySizeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
