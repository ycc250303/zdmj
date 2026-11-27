package com.zdmj.knowledgeService.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 知识引用的不可变快照，来源授权由公开解析入口完成；历史 JSON 只读取声明字段。
 *
 * @param documentId 正数文档编号，对应 knowledge_documents.id
 * @param chunkIndex 非负分块编号，历史缺省时为 null
 * @param score 有限余弦相似度，范围 [-1,1]，历史缺省时为 null
 * @param title 展示标题，不含内部文件标识
 * @param snippet 生成时的展示片段
 * @param docCategory 业务分类，可为空
 * @param downloadPath 本人可用原文件的受鉴权路径，不持久化到报告
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeReference(Long documentId, Integer chunkIndex, Double score, String title,
        String snippet, String docCategory, @JsonIgnore String downloadPath) {

    public KnowledgeReference {
        if (documentId == null || documentId <= 0 || (chunkIndex != null && chunkIndex < 0)
                || (score != null && (!Double.isFinite(score) || score < -1 || score > 1))) {
            throw new IllegalArgumentException("知识引用编号、分块或得分非法");
        }
    }

    /** 历史来源按原始 JSON 类型读取，非法编号和字段类型立即终止。 */
    @JsonCreator
    private static KnowledgeReference read(JsonNode node) {
        if (!node.isObject()) { throw new IllegalArgumentException("知识引用必须为对象"); }
        JsonNode id = node.get("documentId");
        JsonNode chunk = node.get("chunkIndex");
        JsonNode value = node.get("score");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong()
                || (chunk != null && !chunk.isNull() && (!chunk.isIntegralNumber() || !chunk.canConvertToInt()))
                || (value != null && !value.isNull() && !value.isNumber())) {
            throw new IllegalArgumentException("知识引用编号、分块或得分类型非法");
        }
        return new KnowledgeReference(id.longValue(), chunk == null || chunk.isNull() ? null : chunk.intValue(),
                value == null || value.isNull() ? null : value.doubleValue(), text(node, "title"),
                text(node, "snippet"), text(node, "docCategory"), null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) { return null; }
        if (!value.isTextual()) { throw new IllegalArgumentException("知识引用文本字段类型非法"); }
        return value.textValue();
    }

    /** 兼容报告来源展示，只返回声明过的字段。 */
    @JsonProperty("metadata")
    public KnowledgeReferenceMetadata metadata() { return new KnowledgeReferenceMetadata(title, docCategory); }
}
