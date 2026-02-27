package com.zdmj.common.util.json;

import java.util.Set;

public class ProjectCodeVectorsValidator {
    private static final JsonStructureRule VALIDATION_RULE = JsonStructureRule.builder()
            .rootType(JsonStructureRule.RootType.ARRAY)
            .arrayElementRule(JsonFieldRule.builder()
                    .fieldName("item")
                    .type(JsonFieldRule.JsonNodeType.OBJECT)
                    .nestedRules(Set.of(
                            JsonFieldRule.builder()
                                    .fieldName("source")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("language")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("functionName")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("startLine")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.NUMBER)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("endLine")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.NUMBER)
                                    .build()
                    ))
                    .build())
            .strictMode(true)
            .errorMessagePrefix("岗位向量metadata")
            .build();

    /**
     * 验证并清理 content JSON，确保只有 type 和 content 字段
     * 使用通用验证器进行验证
     */
    public static String validate(String contentJson) {
        return GenericJsonValidator.validate(contentJson, VALIDATION_RULE);
    }
}
