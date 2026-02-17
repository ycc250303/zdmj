package com.zdmj.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.exception.BusinessException;

public class SkillContentValidator {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证并清理 content JSON，确保只有 type 和 content 字段
     * 优化版本：减少不必要的 JSON 序列化，优化字段检查逻辑
     */
    public static String validate(String contentJson) {
        try {
            JsonNode root = objectMapper.readTree(contentJson);

            if (!root.isArray()) {
                throw new BusinessException(400, "content 必须是数组格式");
            }

            int arraySize = root.size();

            for (int i = 0; i < arraySize; i++) {
                JsonNode item = root.get(i);

                if (!item.isObject()) {
                    throw new BusinessException(400, "content 数组中的元素必须是对象");
                }

                // 检查必需字段
                if (!item.has("type") || !item.has("content")) {
                    throw new BusinessException(400, "每个技能项必须包含 type 和 content 字段");
                }

                // 检查字段数量：如果字段数不等于2，说明有额外字段，直接报错
                if (item.size() != 2) {
                    // 找出不允许的字段并报错
                    item.fieldNames().forEachRemaining(fieldName -> {
                        if (!"type".equals(fieldName) && !"content".equals(fieldName)) {
                            throw new BusinessException(400,
                                    String.format("技能项中不允许包含 '%s' 字段，只允许 type 和 content", fieldName));
                        }
                    });
                }

                // 验证 content 字段是数组
                JsonNode contentField = item.get("content");
                if (!contentField.isArray()) {
                    throw new BusinessException(400, "content 字段必须是数组");
                }
            }

            // 验证通过，直接返回原字符串（避免不必要的 JSON 序列化）
            return contentJson;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "content JSON 格式错误: " + e.getMessage());
        }
    }
}
