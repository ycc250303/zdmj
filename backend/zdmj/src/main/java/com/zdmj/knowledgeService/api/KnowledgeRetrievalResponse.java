package com.zdmj.knowledgeService.api;

import java.util.List;

/**
 * 知识库排序检索结果：归一化问题、改写信息和合并排序后的片段。
 *
 * @param query 归一化后的原始问句
 * @param rewrittenQuery 检索改写问句；未改写时与 {@link #query} 相同
 * @param topK 本路 ANN 的 topK（由问句长度决定）
 * @param minScore 相似度下限
 * @param rewriteUsed 是否使用不同的改写问句执行双路检索
 * @param hits 按 score 降序的命中（双路合并去重后）
 */
public record KnowledgeRetrievalResponse(
        String query,
        String rewrittenQuery,
        Integer topK,
        Double minScore,
        boolean rewriteUsed,
        List<KnowledgeHit> hits) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public KnowledgeRetrievalResponse {
        hits = hits == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(hits));
    }
}
