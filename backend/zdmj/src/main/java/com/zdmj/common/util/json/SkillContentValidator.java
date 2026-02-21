package com.zdmj.common.util.json;

import java.util.Set;

/**
 * 技能内容验证器
 * 使用通用验证器进行验证，避免重复代码
 * 
 * 验证规则：
 * - 根节点必须是数组
 * - 数组元素必须是对象
 * - 对象必须包含 type（字符串）和 content（数组）字段
 * - 严格模式：不允许其他字段
 */
public class SkillContentValidator {
    // 定义验证规则（使用静态初始化，避免每次调用都创建）
    private static final JsonStructureRule VALIDATION_RULE = JsonStructureRule.builder()
            .rootType(JsonStructureRule.RootType.ARRAY)
            .arrayElementRule(JsonFieldRule.builder()
                    .fieldName("item")
                    .type(JsonFieldRule.JsonNodeType.OBJECT)
                    .nestedRules(Set.of(
                            JsonFieldRule.builder()
                                    .fieldName("type")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("content")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.ARRAY)
                                    .build()))
                    .build())
            .strictMode(true)
            .errorMessagePrefix("技能内容")
            .build();

    /**
     * 验证并清理 content JSON，确保只有 type 和 content 字段
     * 使用通用验证器进行验证
     */
    public static String validate(String contentJson) {
        return GenericJsonValidator.validate(contentJson, VALIDATION_RULE);
    }
}
