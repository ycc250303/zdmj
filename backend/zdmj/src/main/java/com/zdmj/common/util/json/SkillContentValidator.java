package com.zdmj.common.util.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 技能内容验证器
 * 
 * 验证规则：
 * - 根节点必须是数组
 * - 数组元素必须是对象
 * - 对象必须包含 type（字符串）和 content（数组）字段
 * - 严格模式：不允许其他字段
 */
public class SkillContentValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证并清理 content JSON，确保只有 type 和 content 字段
     */
    public static String validate(String contentJson) {
        if (contentJson == null || contentJson.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), "技能内容不能为空");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            if (!root.isArray()) {
                throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), "技能内容必须是数组格式");
            }

            for (int i = 0; i < root.size(); i++) {
                JsonNode item = root.get(i);
                String path = "skills[" + i + "]";
                if (!item.isObject()) {
                    throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), path + " 必须是对象");
                }
                if (!item.has("type") || !item.get("type").isTextual()) {
                    throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), path + ".type 必须是字符串");
                }
                if (!item.has("content") || !item.get("content").isArray()) {
                    throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), path + ".content 必须是数组");
                }
                // 严格模式：只允许 type/content 两个字段
                if (item.size() != 2 || !item.has("type") || !item.has("content")) {
                    throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(),
                            path + " 只允许 type 和 content 字段");
                }
            }
            return contentJson;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), "技能内容 JSON 格式非法: " + e.getMessage());
        }
    }
}
