package com.zdmj.common.async;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbStringTypeHandler;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Redis Stream 异步任务，对应表 {@code async_llm_tasks}。
 *
 * <p>状态真相在本表；Stream 消息只带 {@code taskId}。进行中行占部分唯一索引
 * {@code uk_async_llm_tasks_inflight(task_type, biz_key)}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "async_llm_tasks", autoResultMap = true)
public class AsyncLlmTask extends BaseEntity {

    /**
     * 主键，即对外 {@code taskId}（前端轮询 {@code GET /async-tasks/{id}}）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发起人 userId（逻辑外键 users.id）。消费者无登录态，用此字段写 UserHolder 与鉴权。
     */
    private Long userId;

    /**
     * 任务类型码，见 {@link AsyncTaskType#getCode()}（1–10）
     */
    private Integer taskType;

    /**
     * 去重键，与 taskType 组成进行中唯一约束。
     * 例：{@code user:{userId}}、{@code user:{userId}:job:{jobId}}、{@code report:{reportId}}、{@code doc:{documentId}}
     */
    private String bizKey;

    /**
     * 状态码，见 {@link AsyncTaskStatus#getCode()}：1 PENDING / 2 RUNNING / 3 SUCCESS / 4 FAILED
     */
    private Integer status;

    /**
     * 入队参数 JSONB 文本（如匹配 weights、简历 pdfUrl）。Stream 不存正文。
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String payload;

    /**
     * 成功结果 JSONB。仅无独立业务表时使用（如简历识别）；画像/匹配结果写各自业务表，此处可空。
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String result;

    /**
     * 失败摘要，截断后落库，不含堆栈
     */
    private String errorMessage;

    /**
     * 消费者 claim（PENDING→RUNNING）时写入
     */
    private LocalDateTime startedAt;

    /**
     * 进入 SUCCESS / FAILED 时写入
     */
    private LocalDateTime completedAt;
}
