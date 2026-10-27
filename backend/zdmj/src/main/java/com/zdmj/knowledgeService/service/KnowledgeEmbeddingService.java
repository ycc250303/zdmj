package com.zdmj.knowledgeService.service;

public interface KnowledgeEmbeddingService {

    /**
     * 向量化并存储知识库，先删除旧向量再写入新向量。
     *
     * @param knowledgeDocumentId 知识文档 ID
     */
    void vectorizeAndStore(Long knowledgeDocumentId);

    /**
     * 删除知识文档对应的向量。
     *
     * @param knowledgeDocumentId 知识文档 ID
     */
    void deleteVectors(Long knowledgeDocumentId);

    /**
     * 将浮点数组转换为 PostgreSQL 向量字面量。
     *
     * @param vector 浮点数组
     * @return PostgreSQL 向量字符串；空数组返回 {@code null}
     */
    String toPgVector(float[] vector);
}
