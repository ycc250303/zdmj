package com.zdmj.common.async;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 入队立即返回、以及 {@code GET /async-tasks/{id}} 的响应体。
 *
 * <p>{@code status=FAILED} 时带 {@code errorMessage}，HTTP 仍 200 / 业务码 0，不抛 13002。</p>
 */
@Data
public class AsyncTaskDTO {

    /**
     * {@link AsyncLlmTask#getId()}
     */
    private Long taskId;

    /**
     * {@link AsyncTaskType#getCode()}
     */
    private Integer taskType;

    /**
     * {@link AsyncTaskStatus#getCode()}：1 排队 / 2 执行中 / 3 成功 / 4 失败
     */
    private Integer status;

    /**
     * 失败摘要；成功时为 {@code null}
     */
    private String errorMessage;

    /**
     * 无独立业务表时的成功 JSON（如简历识别）；其它类型一般为 {@code null}，结果走原业务 GET
     */
    private String result;

    /**
     * claim 开始时间；尚未执行时为 {@code null}
     */
    private LocalDateTime startedAt;

    /**
     * 终态时间；进行中为 {@code null}
     */
    private LocalDateTime completedAt;
}
