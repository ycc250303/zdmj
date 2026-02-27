package com.zdmj.common.util.json;

import java.util.Set;

public class JobVectorsValidator {
    private static final JsonStructureRule VALIDATION_RULE = JsonStructureRule.builder()
            .rootType(JsonStructureRule.RootType.ARRAY)
            .arrayElementRule(JsonFieldRule.builder()
                    .fieldName("item")
                    .type(JsonFieldRule.JsonNodeType.OBJECT)
                    .nestedRules(Set.of(
                            JsonFieldRule.builder()
                                    .fieldName("job_name")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("company_name")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("location")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
                                    .build(),
                            JsonFieldRule.builder()
                                    .fieldName("salary")
                                    .required(true)
                                    .type(JsonFieldRule.JsonNodeType.STRING)
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
