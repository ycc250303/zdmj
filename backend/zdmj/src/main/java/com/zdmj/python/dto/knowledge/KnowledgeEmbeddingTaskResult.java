package com.zdmj.python.dto.knowledge;

import lombok.Data;

/**
 * 知识库向量化任务创建结果
 * 对应 Python 接口返回：KnowledgeEmbeddingTaskResult
 */
@Data
public class KnowledgeEmbeddingTaskResult {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态：PENDING / RUNNING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 提示信息
     */
    private String message;
}
