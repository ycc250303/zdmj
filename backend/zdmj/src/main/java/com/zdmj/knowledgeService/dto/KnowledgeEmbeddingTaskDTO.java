package com.zdmj.knowledgeService.dto;

import lombok.Data;
import java.util.List;
@Data
public class KnowledgeEmbeddingTaskDTO {
    /**
     * 任务ID
     */
    private String taskId;
    /**
     * 知识库ID
     */
    private Long knowledgeId;
    /**
     * 任务状态
     */
    private String status;          // PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
    /**
     * 向量ID
     */
    private List<Long> vectorIds;   // SUCCESS 时通常有值
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 开始时间
     */
    private String startTime;
    /**
     * 结束时间
     */
    private String endTime;
}
