package com.zdmj.common.util.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * JSON 字段验证规则
 * 用于定义单个字段的验证要求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonFieldRule {
    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 是否必需字段
     */
    @Builder.Default
    private boolean required = false;

    /**
     * 字段类型（STRING, NUMBER, BOOLEAN, ARRAY, OBJECT, NULL）
     */
    private JsonNodeType type;

    /**
     * 如果字段是对象类型，可以定义嵌套的字段规则
     */
    private Set<JsonFieldRule> nestedRules;

    /**
     * 如果字段是数组类型，可以定义数组元素的验证规则
     */
    private JsonFieldRule arrayElementRule;

    /**
     * 自定义错误消息
     */
    private String errorMessage;

    /**
     * JSON 节点类型枚举
     */
    public enum JsonNodeType {
        STRING,
        NUMBER,
        BOOLEAN,
        ARRAY,
        OBJECT,
        NULL,
        ANY // 任意类型
    }
}
