package com.zdmj.conversationService.dto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 创建会话请求。检索范围只接受 useSystemKnowledge、ragDocumentIds，其余键在保存时丢弃。
 *
 * @param config 对话配置，创建后、发出首条消息前可改；开关仅接受布尔值，文档编号仅接受 Long 范围内的正整数数组
 */
public record ConversationRequest(
        Map<String, Object> config) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public ConversationRequest {
        config = config == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(config));
    }

    /**
     * 读取并校验系统知识库开关。
     *
     * @return 布尔开关；配置、字段缺省或为 null 时返回 false
     * @throws IllegalArgumentException 开关类型不为 Boolean
     */
    public boolean useSystemKnowledge() {
        Object raw = config == null ? null : config.get("useSystemKnowledge");
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("useSystemKnowledge 必须为布尔值");
    }

    /**
     * 将整数数组解析为用户文档编号，逐项检查类型和取值。
     *
     * @return null 表示全部用户文档，空列表表示关闭用户文档检索；非空结果保持原顺序与重复值
     * @throws IllegalArgumentException 输入非列表，或元素为空、非整数、非正数、超出 Long 范围
     */
    public List<Long> ragDocumentIds() {
        Object raw = config == null ? null : config.get("ragDocumentIds");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("ragDocumentIds 必须为数组或 null");
        }
        List<Long> ids = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            Object item = list.get(index);
            long id;
            if (item instanceof BigInteger integer) {
                try {
                    id = integer.longValueExact();
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("ragDocumentIds[" + index + "] 超出编号取值范围", exception);
                }
            } else if (item instanceof Byte || item instanceof Short || item instanceof Integer || item instanceof Long) {
                id = ((Number) item).longValue();
            } else {
                throw new IllegalArgumentException("ragDocumentIds[" + index + "] 必须为整数");
            }
            if (id <= 0) {
                throw new IllegalArgumentException("ragDocumentIds[" + index + "] 必须为正整数");
            }
            ids.add(id);
        }
        return ids;
    }
}
