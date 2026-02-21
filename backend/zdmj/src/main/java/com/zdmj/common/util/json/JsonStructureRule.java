package com.zdmj.common.util.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * JSON 结构验证规则
 * 用于定义整个 JSON 结构的验证要求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonStructureRule {
    /**
     * 根节点类型（ARRAY 或 OBJECT）
     */
    private RootType rootType;

    /**
     * 如果根节点是对象，定义允许的字段规则
     */
    private Set<JsonFieldRule> objectFields;

    /**
     * 如果根节点是数组，定义数组元素的验证规则
     */
    private JsonFieldRule arrayElementRule;

    /**
     * 是否严格模式：如果为 true，对象中不允许出现未定义的字段
     */
    @Builder.Default
    private boolean strictMode = true;

    /**
     * 自定义错误消息前缀
     */
    private String errorMessagePrefix;

    /**
     * 根节点类型枚举
     */
    public enum RootType {
        ARRAY,
        OBJECT
    }
}
