package com.zdmj.knowledgeService.enums;

import java.util.Arrays;

public enum KnowledgeVectorTaskStatusEnum {
    PENDING(1, "PENDING"),
    RUNNING(2, "RUNNING"),
    SUCCESS(3, "SUCCESS"),
    FAILED(4, "FAILED");

    private final int code;
    private final String name;

    KnowledgeVectorTaskStatusEnum(int code, String name) {
        this.code = code;   
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static KnowledgeVectorTaskStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
