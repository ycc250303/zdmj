package com.zdmj.common.async;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 入队去重与任务查询。Controller 只调本类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskService {

    private final AsyncLlmTaskMapper asyncLlmTaskMapper;
    private final LlmStreamProducer llmStreamProducer;

    /**
     * 去重入队：INSERT PENDING → XADD {@code taskId}。
     *
     * <ol>
     *   <li>插入 PENDING。撞部分唯一索引则返回已有进行中任务，不再 XADD。</li>
     *   <li>XADD {@code taskId}。失败由 Producer 把该行标 FAILED，避免孤儿 PENDING 占坑。</li>
     * </ol>
     *
     * 互斥靠 {@code uk_async_llm_tasks_inflight}。
     *
     * @param type        须为 {@link AsyncTaskType.StreamKind#LLM}（向量任务四期另走 embed 流）
     * @param userId      发起人，写入任务行
     * @param bizKey      去重键，如 {@code user:8}、{@code user:8:job:3}
     * @param payloadJson 入队参数 JSON，可空
     * @return 新任务或已有进行中任务；XADD 失败时 {@code status=FAILED}
     */
    public AsyncTaskDTO enqueue(AsyncTaskType type, long userId, String bizKey, String payloadJson) {
        if (type.getStreamKind() != AsyncTaskType.StreamKind.LLM) {
            throw new IllegalArgumentException("LLM 流不接受向量任务: type=" + type);
        }

        AsyncLlmTask task = new AsyncLlmTask();
        task.setUserId(userId);
        task.setTaskType(type.getCode());
        task.setBizKey(bizKey);
        task.setStatus(AsyncTaskStatus.PENDING.getCode());
        task.setPayload(payloadJson);
        try {
            asyncLlmTaskMapper.insert(task);
        } catch (DataIntegrityViolationException e) {
            AsyncLlmTask inflight = findInFlight(type, bizKey);
            if (inflight != null) {
                return toDto(inflight);
            }
            throw new BusinessException(ErrorCode.SYSTEM_EXCEPTION, "任务入队冲突", e);
        }

        boolean sent = llmStreamProducer.send(task.getId());
        AsyncLlmTask stored = asyncLlmTaskMapper.selectById(task.getId());
        if (stored == null) {
            throw new BusinessException(ErrorCode.SYSTEM_EXCEPTION, "入队后任务丢失");
        }
        if (!sent) {
            log.warn("XADD 失败，任务已标 FAILED: taskId={}", task.getId());
        }
        return toDto(stored);
    }

    /**
     * 查询任务。须已登录。
     *
     * @param taskId 路径上的任务 id
     * @return 本人任务（含 FAILED）
     * @throws BusinessException {@link ErrorCode#ASYNC_TASK_NOT_FOUND} 不存在或非本人（不泄露他人任务）
     */
    public AsyncTaskDTO getById(Long taskId) {
        Long userId = UserHolder.requireUserId();
        AsyncLlmTask task = asyncLlmTaskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND);
        }
        return toDto(task);
    }

    /** 同一 (taskType, bizKey) 且 status∈{PENDING,RUNNING} 的行，至多一条。 */
    private AsyncLlmTask findInFlight(AsyncTaskType type, String bizKey) {
        return asyncLlmTaskMapper.selectOne(new LambdaQueryWrapper<AsyncLlmTask>()
                .eq(AsyncLlmTask::getTaskType, type.getCode())
                .eq(AsyncLlmTask::getBizKey, bizKey)
                .in(AsyncLlmTask::getStatus, AsyncTaskStatus.PENDING.getCode(), AsyncTaskStatus.RUNNING.getCode()));
    }

    /** Entity → 轮询/入队响应，不含 payload。 */
    static AsyncTaskDTO toDto(AsyncLlmTask task) {
        AsyncTaskDTO dto = new AsyncTaskDTO();
        dto.setTaskId(task.getId());
        dto.setTaskType(task.getTaskType());
        dto.setStatus(task.getStatus());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setResult(task.getResult());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        return dto;
    }
}
