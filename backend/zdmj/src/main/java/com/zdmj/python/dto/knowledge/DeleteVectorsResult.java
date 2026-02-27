package com.zdmj.python.dto.knowledge;

import lombok.Data;

/**
 * 整库向量删除任务创建结果
 * 对应 Python 接口返回：DeleteVectorsResult
 */
@Data
public class DeleteVectorsResult {

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
