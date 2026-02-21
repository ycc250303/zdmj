package com.zdmj.common.util.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.exception.BusinessException;

import java.util.HashSet;
import java.util.Set;

/**
 * 通用 JSON 验证器
 * 通过配置规则来验证不同的 JSON 结构，避免为每个字段都写一个验证器类
 * 
 * 使用示例：
 * 
 * <pre>
 * // 定义验证规则
 * JsonStructureRule rule = JsonStructureRule.builder()
 *         .rootType(JsonStructureRule.RootType.ARRAY)
 *         .arrayElementRule(JsonFieldRule.builder()
 *                 .fieldName("item")
 *                 .type(JsonFieldRule.JsonNodeType.OBJECT)
 *                 .nestedRules(Set.of(
 *                         JsonFieldRule.builder()
 *                                 .fieldName("type")
 *                                 .required(true)
 *                                 .type(JsonFieldRule.JsonNodeType.STRING)
 *                                 .build(),
 *                         JsonFieldRule.builder()
 *                                 .fieldName("content")
 *                                 .required(true)
 *                                 .type(JsonFieldRule.JsonNodeType.ARRAY)
 *                                 .build()))
 *                 .build())
 *         .strictMode(true)
 *         .build();
 * 
 * // 验证 JSON
 * String validatedJson = GenericJsonValidator.validate(jsonString, rule);
 * </pre>
 */
public class GenericJsonValidator {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证 JSON 字符串是否符合指定的规则
     * 
     * @param jsonString 要验证的 JSON 字符串
     * @param rule       验证规则
     * @return 验证通过后返回原 JSON 字符串（避免不必要的序列化）
     * @throws BusinessException 如果验证失败
     */
    public static String validate(String jsonString, JsonStructureRule rule) {
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            String errorPrefix = rule.getErrorMessagePrefix() != null
                    ? rule.getErrorMessagePrefix()
                    : "JSON";

            // 验证根节点类型
            validateRootType(root, rule.getRootType(), errorPrefix);

            // 根据根节点类型进行不同的验证
            if (rule.getRootType() == JsonStructureRule.RootType.ARRAY) {
                validateArray(root, rule.getArrayElementRule(), rule.isStrictMode(), errorPrefix);
            } else if (rule.getRootType() == JsonStructureRule.RootType.OBJECT) {
                validateObject(root, rule.getObjectFields(), rule.isStrictMode(), errorPrefix);
            }

            // 验证通过，返回原字符串
            return jsonString;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400,
                    (rule.getErrorMessagePrefix() != null ? rule.getErrorMessagePrefix() : "JSON")
                            + " 格式错误: " + e.getMessage());
        }
    }

    /**
     * 验证根节点类型
     */
    private static void validateRootType(JsonNode root, JsonStructureRule.RootType expectedType, String errorPrefix) {
        if (expectedType == JsonStructureRule.RootType.ARRAY && !root.isArray()) {
            throw new BusinessException(400, errorPrefix + " 必须是数组格式");
        } else if (expectedType == JsonStructureRule.RootType.OBJECT && !root.isObject()) {
            throw new BusinessException(400, errorPrefix + " 必须是对象格式");
        }
    }

    /**
     * 验证数组结构
     */
    private static void validateArray(JsonNode arrayNode, JsonFieldRule elementRule, boolean strictMode,
            String errorPrefix) {
        if (elementRule == null) {
            return; // 如果没有定义元素规则，只验证是数组即可
        }

        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode element = arrayNode.get(i);
            validateField(element, elementRule, strictMode, errorPrefix + "[" + i + "]");
        }
    }

    /**
     * 验证对象结构
     */
    private static void validateObject(JsonNode objectNode, Set<JsonFieldRule> fieldRules, boolean strictMode,
            String errorPrefix) {
        if (fieldRules == null || fieldRules.isEmpty()) {
            return; // 如果没有定义字段规则，只验证是对象即可
        }

        // 收集所有允许的字段名
        Set<String> allowedFields = new HashSet<>();
        for (JsonFieldRule rule : fieldRules) {
            allowedFields.add(rule.getFieldName());
        }

        // 验证每个定义的字段规则
        for (JsonFieldRule rule : fieldRules) {
            String fieldName = rule.getFieldName();
            JsonNode fieldNode = objectNode.get(fieldName);

            if (rule.isRequired() && (fieldNode == null || fieldNode.isNull())) {
                throw new BusinessException(400,
                        String.format("%s 中必须包含 '%s' 字段", errorPrefix, fieldName));
            }

            if (fieldNode != null && !fieldNode.isNull()) {
                validateField(fieldNode, rule, strictMode, errorPrefix + "." + fieldName);
            }
        }

        // 严格模式：检查是否有未定义的字段
        if (strictMode) {
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                if (!allowedFields.contains(fieldName)) {
                    throw new BusinessException(400,
                            String.format("%s 中不允许包含 '%s' 字段，允许的字段: %s",
                                    errorPrefix, fieldName, allowedFields));
                }
            });
        }
    }

    /**
     * 验证单个字段
     */
    private static void validateField(JsonNode fieldNode, JsonFieldRule rule, boolean strictMode, String fieldPath) {
        // 验证字段类型
        validateFieldType(fieldNode, rule.getType(), fieldPath);

        // 如果是对象类型且有嵌套规则，递归验证
        if (fieldNode.isObject() && rule.getNestedRules() != null && !rule.getNestedRules().isEmpty()) {
            validateObject(fieldNode, rule.getNestedRules(), strictMode, fieldPath);
        }

        // 如果是数组类型且有元素规则，递归验证数组元素
        if (fieldNode.isArray() && rule.getArrayElementRule() != null) {
            validateArray(fieldNode, rule.getArrayElementRule(), strictMode, fieldPath);
        }
    }

    /**
     * 验证字段类型
     */
    private static void validateFieldType(JsonNode node, JsonFieldRule.JsonNodeType expectedType, String fieldPath) {
        if (expectedType == null || expectedType == JsonFieldRule.JsonNodeType.ANY) {
            return; // 任意类型，不验证
        }

        boolean typeMatch = false;
        switch (expectedType) {
            case STRING:
                typeMatch = node.isTextual();
                break;
            case NUMBER:
                typeMatch = node.isNumber();
                break;
            case BOOLEAN:
                typeMatch = node.isBoolean();
                break;
            case ARRAY:
                typeMatch = node.isArray();
                break;
            case OBJECT:
                typeMatch = node.isObject();
                break;
            case NULL:
                typeMatch = node.isNull();
                break;
        }

        if (!typeMatch) {
            throw new BusinessException(400,
                    String.format("%s 字段必须是 %s 类型", fieldPath, expectedType.name().toLowerCase()));
        }
    }
}
