package com.zdmj.knowledgeService.service;

public interface KnowledgeEmbeddingService {
    /**
     * 创建向量化任务（PENDING）并返回任务ID
     * @param knowledgeDocumentId 知识文档ID
     * 
     * @param userId 用户ID
     * @return 任务ID
     */
    Long submitVectorizeTask(Long documentId);

    /**
     * 创建删除任务（PENDING）并返回任务ID
     * @param documentId 知识文档ID
     * @param userId 用户ID
     * @return 任务ID
    */
    Long submitDeleteTask(Long documentId);

    /**
     * 异步执行任务
     * @param taskId 任务ID
     */
    void executeTaskAsync(Long taskId);

    /**
     * 向量化并存储知识库(先删除旧向量后向量化)
    * @param knowledgeDocumentId 知识文档ID
     */
    void vectorizeAndStore(Long knowledgeDocumentId);

    /**
     * 删除知识库向量
     * @param knowledgeDocumentId 知识文档ID
     */
    void deleteVectors(Long knowledgeDocumentId);
}
