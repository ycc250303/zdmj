package com.zdmj.common.async.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.common.async.AsyncLlmTask;

/**
 * 异步 LLM 任务 Mapper。claim / success / fail 与向量任务同款原子更新。
 */
@Mapper
public interface AsyncLlmTaskMapper extends BaseMapper<AsyncLlmTask> {

    /**
     * 原子抢占：PENDING(1) 或 RUNNING(2) → RUNNING(2)。
     * 重启后行可能已是 RUNNING；{@code started_at} 仅在空时写入。
     *
     * @param taskId {@code async_llm_tasks.id}
     * @return 1 抢占成功；0 已是终态（重复投递应 ACK 跳过）
     */
    int claimPendingTask(@Param("taskId") Long taskId);

    /**
     * 仅 RUNNING(2) → SUCCESS(3)，写入 {@code completed_at}，清空 {@code error_message}。
     *
     * @param result 可空 JSONB；无独立结果表时落此列
     */
    int markTaskSuccess(@Param("taskId") Long taskId, @Param("result") String result);

    /**
     * 仅 RUNNING(2) → FAILED(4)。
     *
     * @param errorMessage 截断后的失败摘要
     */
    int markTaskFailed(@Param("taskId") Long taskId, @Param("errorMessage") String errorMessage);

    /**
     * XADD 失败：PENDING/RUNNING → FAILED，避免孤儿行占 {@code uk_async_llm_tasks_inflight}。
     */
    int markEnqueueFailed(@Param("taskId") Long taskId, @Param("errorMessage") String errorMessage);
}
