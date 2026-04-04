package com.zdmj.knowledgeService.dto;

import lombok.Data;

@Data
public class KnowledgeEmbeddingTaskCreateDTO {
    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private String status; // PENDING / RUNNING / SUCCESS / FAILED / CANCELLED

    /**
     * 知识库ID
     */
    private String message;
}
