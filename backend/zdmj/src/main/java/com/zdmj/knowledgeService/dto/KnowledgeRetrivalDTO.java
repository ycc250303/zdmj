package com.zdmj.knowledgeService.dto;

import java.util.Map;

/**
 * 知识检索命中结果。
 *
 * @param id 知识库向量ID
 * @param documentId 文档ID
 * @param chunkIndex 文档块索引
 * @param content 文档块内容
 * @param score 余弦相似度，范围约为 [-1, 1]
 * @param metadata 文档块元数据
 * @param docCategory 文档分类（来自 knowledge_documents.metadata.docCategory，检索时 JOIN 填充）
 */
public record KnowledgeRetrivalDTO(
        Long id,
        Long documentId,
        Integer chunkIndex,
        String content,
        Double score,
        Map<String, Object> metadata,
        String docCategory) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public KnowledgeRetrivalDTO {
        metadata = metadata == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
    }
}
