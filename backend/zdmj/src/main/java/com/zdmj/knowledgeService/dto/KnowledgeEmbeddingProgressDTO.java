package com.zdmj.knowledgeService.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class KnowledgeEmbeddingProgressDTO {
    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 最近一次向量化任务ID
     */
    private Long vectorTaskId;

    /**
     * 任务状态名称（PENDING/RUNNING/SUCCESS/FAILED）
     */
    private String taskStatusName;

    /**
     * 已写入分块数量
     */
    private Integer chunkCount;

    /**
     * 最近错误信息
     */
    private String lastError;

    /**
     * 任务开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 任务完成时间
     */
    private LocalDateTime completedAt;
}
