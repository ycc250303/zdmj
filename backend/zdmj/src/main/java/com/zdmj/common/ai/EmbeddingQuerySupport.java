package com.zdmj.common.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 检索侧查询向量生成（与入库 {@link EmbeddingModel#embed} 解耦，供 RAG / 报告检索复用）。
 */
@Slf4j
public final class EmbeddingQuerySupport {

    private EmbeddingQuerySupport() {
    }

    /**
     * @param embeddingModel 嵌入模型
     * @param queryText      查询文本
     * @return 向量；失败或空输入时返回 {@code null}
     */
    public static float[] embedQuery(EmbeddingModel embeddingModel, String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return null;
        }
        try {
            return embeddingModel.embed(queryText);
        } catch (Exception e) {
            log.warn("查询向量生成失败: {}", e.getMessage());
            return null;
        }
    }
}
