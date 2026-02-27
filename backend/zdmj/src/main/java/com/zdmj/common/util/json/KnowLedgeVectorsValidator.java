package com.zdmj.common.util.json;

import java.util.Set;

public class KnowLedgeVectorsValidator {
    private static final JsonStructureRule VALIDATION_RULE = JsonStructureRule.builder()
            .rootType(JsonStructureRule.RootType.ARRAY)
            .arrayElementRule(JsonFieldRule.builder()
                    .fieldName("item")
                    .type(JsonFieldRule.JsonNodeType.OBJECT)
                    .nestedRules(Set.of(
                            JsonFieldRule.builder()
                                    .fieldName("knowledgeId")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.NUMBER)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("source")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build()))
                    .build())
            .strictMode(true)
            .errorMessagePrefix("知识库向量metadata")
            .build();

    /**
     * 验证并清理 content JSON，确保只有 type 和 content 字段
     * 使用通用验证器进行验证
     */
    public static String validate(String contentJson) {
        return GenericJsonValidator.validate(contentJson, VALIDATION_RULE);
    }
}
