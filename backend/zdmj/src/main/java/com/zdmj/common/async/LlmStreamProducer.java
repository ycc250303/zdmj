package com.zdmj.common.async;

import org.springframework.stereotype.Component;

import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM 流生产者：{@code XADD} 仅 {@code taskId}；失败把任务行标 FAILED。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmStreamProducer {

    private static final int ERROR_MAX_LEN = 500;

    private final RedisUtil redisUtil;
    private final AsyncLlmTaskMapper asyncLlmTaskMapper;

    /**
     * {@code XADD} 标识字段 {@code taskId}。正文在 DB payload。
     *
     * @param taskId {@code async_llm_tasks.id}
     * @return {@code true} 已写入 Stream；{@code false} 已将行标 FAILED
     */
    public boolean send(long taskId) {
        try {
            redisUtil.xaddTask(RedisConstants.LLM_STREAM_KEY, taskId);
            log.info("XADD {}: taskId={}", RedisConstants.LLM_STREAM_KEY, taskId);
            return true;
        } catch (Exception e) {
            log.error("XADD 失败: stream={}, taskId={}", RedisConstants.LLM_STREAM_KEY, taskId, e);
            onSendFailed(taskId, truncateError("任务入队失败: " + e.getMessage()));
            return false;
        }
    }

    /**
     * XADD 失败：PENDING/RUNNING → FAILED，避免进行中唯一索引被占死。
     */
    private void onSendFailed(long taskId, String error) {
        int n = asyncLlmTaskMapper.markEnqueueFailed(taskId, error);
        if (n != 1) {
            log.warn("入队失败回写未更新行: taskId={}, updated={}", taskId, n);
        }
    }

    private static String truncateError(String error) {
        if (error == null) {
            return "未知错误";
        }
        return error.length() > ERROR_MAX_LEN ? error.substring(0, ERROR_MAX_LEN) : error;
    }
}
