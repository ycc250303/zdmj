package com.zdmj.python.dto.knowledge;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 向量化/删除任务状态响应
 * 对应 Python 接口：GET /ai/knowledge/embedding/tasks/{taskId}
 */
@Data
public class TaskStatusResponse {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 关联的知识库ID
     */
    private Long knowledgeId;

    /**
     * 任务状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
     */
    private String status;

    /**
     * 任务成功后生成或保留的全部向量ID列表
     */
    private List<Long> vectorIds;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 任务开始时间（ISO8601）
     */
    private String startTime;

    /**
     * 任务结束时间（ISO8601）
     */
    private String endTime;
}
