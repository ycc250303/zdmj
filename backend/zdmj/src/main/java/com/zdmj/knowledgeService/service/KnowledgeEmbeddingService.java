package com.zdmj.knowledgeService.service;

import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;

public interface KnowledgeEmbeddingService {
    /**
     * 创建向量化任务（PENDING）并返回任务ID
     * 
     * @param knowledgeDocumentId 知识文档ID
     * 
     * @param userId              用户ID
     * @return 任务ID
     */
    Long submitVectorizeTask(Long documentId);

    /**
     * 创建删除任务（PENDING）并返回任务ID
     * 
     * @param documentId 知识文档ID
     * @param userId     用户ID
     * @return 任务ID
     */
    Long submitDeleteTask(Long documentId);

    /**
     * 已 claim 的向量任务：按类型执行 embedding 或删除。成功/失败由消费者标任务行。
     *
     * @param task 已抢占为 RUNNING 的行
     */
    void executeClaimed(KnowledgeVectorTask task);

    /**
     * 向量化并存储知识库(先删除旧向量后向量化)
     * 
     * @param knowledgeDocumentId 知识文档ID
     */
    void vectorizeAndStore(Long knowledgeDocumentId);

    /**
     * 删除知识库向量
     * 
     * @param knowledgeDocumentId 知识文档ID
     */
    void deleteVectors(Long knowledgeDocumentId);

    /**
     * 将浮点数数组转换为 PostgreSQL 向量字符串
     * 
     * @param vector 浮点数数组
     * @return PostgreSQL 向量字符串
     */
    String toPgVector(float[] vector);
}
