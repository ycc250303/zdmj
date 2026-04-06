package com.zdmj.knowledgeService.enums;

import java.util.Arrays;

public enum KnowledgeVectorTaskTypeEnum {
    EMBEDDING(1, "EMBEDDING"),
    DELETE(2, "DELETE");

    private final int code;
    private final String name;

    KnowledgeVectorTaskTypeEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static KnowledgeVectorTaskTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
