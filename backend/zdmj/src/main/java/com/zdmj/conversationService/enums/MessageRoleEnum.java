package com.zdmj.conversationService.enums;

/**
 * 消息角色枚举
 */
public enum MessageRoleEnum {
    USER(1, "user"),
    ASSISTANT(2, "assistant"),
    SYSTEM(3, "system");

    private final int code;
    private final String label;

    MessageRoleEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static MessageRoleEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MessageRoleEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
