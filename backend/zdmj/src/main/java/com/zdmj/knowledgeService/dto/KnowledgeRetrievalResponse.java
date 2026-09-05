package com.zdmj.knowledgeService.dto;

import java.util.List;

import lombok.Data;

/**
 * 知识库排序检索结果：改写后的 query 与 ANN 合并排序列表（尚未整篇展开）。
 */
@Data
public class KnowledgeRetrievalResponse {

    /**
     * 归一化后的原始问句
     */
    private String query;

    /**
     * 检索改写问句；未改写时与 {@link #query} 相同
     */
    private String rewrittenQuery;

    /**
     * 本路 ANN 的 topK（由问句长度决定）
     */
    private Integer topK;

    /**
     * 相似度下限
     */
    private Double minScore;

    /**
     * 是否实际调用了改写模型
     */
    private boolean rewriteUsed;

    /**
     * 按 score 降序的命中（双路合并去重后）
     */
    private List<KnowledgeRetrivalDTO> hits;
}
