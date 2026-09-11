package com.zdmj.common.async;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 解析 {@code async_llm_tasks.payload} JSONB。非法内容抛运行时异常，由消费者标 FAILED。
 */
public final class AsyncTaskPayloads {

    private AsyncTaskPayloads() {
    }

    /**
     * 将 payload 解析为 JSON 树。
     *
     * @param payload 任务行 JSON 文本，不可空
     * @param mapper  Jackson
     * @return 根节点
     */
    public static JsonNode tree(String payload, ObjectMapper mapper) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("任务 payload 为空");
        }
        try {
            return mapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务 payload 不是合法 JSON", e);
        }
    }

    /**
     * 将 payload 反序列化为指定请求类型。
     *
     * @param payload 任务行 JSON 文本，不可空
     * @param type    目标类型
     * @param mapper  Jackson
     */
    public static <T> T read(String payload, Class<T> type, ObjectMapper mapper) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("任务 payload 为空");
        }
        try {
            return mapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务 payload 无法解析为 " + type.getSimpleName(), e);
        }
    }

    /**
     * 读取正整数 id 字段（如 {@code jobId}/{@code reportId}）。
     *
     * @param node  payload 根节点
     * @param field 字段名
     * @return 大于 0 的 id
     */
    public static long requireId(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new IllegalArgumentException("任务 payload 缺少 " + field);
        }
        JsonNode value = node.get(field);
        try {
            long id = value.isNumber() ? value.longValue() : Long.parseLong(value.asText());
            if (id <= 0) {
                throw new IllegalArgumentException("任务 payload 字段 " + field + " 非法");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("任务 payload 字段 " + field + " 非法", e);
        }
    }

    /**
     * 去掉路径 id 等字段后再转为请求 DTO，避免未知属性反序列化失败。
     *
     * @param node   payload 根对象
     * @param type   目标类型
     * @param mapper Jackson
     * @param drop   需剔除的字段名
     */
    public static <T> T convertRemoving(JsonNode node, Class<T> type, ObjectMapper mapper, String... drop) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("任务 payload 须为 JSON 对象");
        }
        ObjectNode copy = ((ObjectNode) node).deepCopy();
        if (drop != null) {
            for (String field : drop) {
                copy.remove(field);
            }
        }
        try {
            return mapper.convertValue(copy, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("任务 payload 无法解析为 " + type.getSimpleName(), e);
        }
    }

    /**
     * 序列化为入队 payload JSON。
     *
     * @param value  请求 DTO 或 Map
     * @param mapper Jackson
     */
    public static String write(Object value, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("任务 payload 序列化失败", e);
        }
    }
}
