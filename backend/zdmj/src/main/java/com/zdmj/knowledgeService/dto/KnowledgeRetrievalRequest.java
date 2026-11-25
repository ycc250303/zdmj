package com.zdmj.knowledgeService.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库排序检索请求。
 *
 * @param query 非空白原始问题，最多 4000 个 UTF-16 代码单元
 * @param ragDocumentIds Long 范围内的正整数数组；null 表示全部用户文档，空列表关闭用户检索，保留顺序及重复值
 * @param useSystemKnowledge 系统知识库开关，JSON 缺省或 null 时为 false
 */
public record KnowledgeRetrievalRequest(
        @NotBlank(message = "查询内容不能为空")
        @Size(max = 4000, message = "查询长度不能超过4000个字符") String query,
        List<Long> ragDocumentIds,
        boolean useSystemKnowledge) {

    /** 校验问题和编号不变量，并复制集合结构。 */
    public KnowledgeRetrievalRequest {
        if (query == null || query.isBlank() || query.length() > 4000) {
            throw new IllegalArgumentException("query 必须为非空白字符串，长度不能超过4000个字符");
        }
        if (ragDocumentIds != null) {
            for (int index = 0; index < ragDocumentIds.size(); index++) {
                Long id = ragDocumentIds.get(index);
                if (id == null || id <= 0) {
                    throw new IllegalArgumentException("ragDocumentIds[" + index + "] 必须为正整数");
                }
            }
            ragDocumentIds = List.copyOf(ragDocumentIds);
        }
    }

    /**
     * 在 Jackson 类型转换之前检查原始 JSON 类型，再构造不可变请求。
     *
     * @param query 字符串问题
     * @param ids 整数数组或 null
     * @param flag 布尔值或 null
     * @return 校验后的请求
     * @throws IllegalArgumentException 字段类型、编号范围或问题长度非法
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static KnowledgeRetrievalRequest fromJson(@JsonProperty("query") JsonNode query,
            @JsonProperty("ragDocumentIds") JsonNode ids,
            @JsonProperty("useSystemKnowledge") JsonNode flag) {
        if (query == null || !query.isTextual()) {
            throw new IllegalArgumentException("query 必须为字符串");
        }
        if (flag != null && !flag.isNull() && !flag.isBoolean()) {
            throw new IllegalArgumentException("useSystemKnowledge 必须为布尔值");
        }
        List<Long> parsedIds = null;
        if (ids != null && !ids.isNull()) {
            if (!ids.isArray()) {
                throw new IllegalArgumentException("ragDocumentIds 必须为数组或 null");
            }
            parsedIds = new ArrayList<>(ids.size());
            for (int index = 0; index < ids.size(); index++) {
                JsonNode id = ids.get(index);
                if (!id.isIntegralNumber() || !id.canConvertToLong()) {
                    throw new IllegalArgumentException("ragDocumentIds[" + index + "] 必须为 Long 范围内的正整数");
                }
                parsedIds.add(id.longValue());
            }
        }
        return new KnowledgeRetrievalRequest(query.textValue(), parsedIds, flag != null && flag.booleanValue());
    }
}
