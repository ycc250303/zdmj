package com.zdmj.knowledgeService.service;

public interface KnowledgeEmbeddingService {
    /**
     * 创建向量化任务（PENDING）并返回任务ID
     */
    Long submitVectorizeTask(Long knowledgeId, Long userId);

    /**
     * 创建删除任务（PENDING）并返回任务ID
     */
    Long submitDeleteTask(Long knowledgeId, Long userId);

    /**
     * 异步执行任务
     */
    void executeTaskAsync(Long taskId);

    /**
     * 向量化并存储知识库(先删除旧向量后向量化)
     * @param knowledgeId 知识库ID
     */
    void vectorizeAndStore(Long knowledgeId);

    /**
     * 删除知识库向量
     * @param knowledgeId 知识库ID
     */
    void deleteVectors(Long knowledgeId);
}
