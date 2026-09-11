package com.zdmj.common.async;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;

/**
 * LLM Stream 消费者：按 {@code taskType} 分发到域 {@link AsyncTaskExecutor}。
 */
@Component
@ConditionalOnProperty(prefix = "zdmj.async.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmStreamConsumer extends AbstractStreamConsumer implements SmartLifecycle {

    private final AsyncLlmTaskMapper asyncLlmTaskMapper;
    private final Map<Integer, AsyncTaskExecutor> executors;

    private volatile boolean running;

    public LlmStreamConsumer(RedisUtil redisUtil, AsyncLlmTaskMapper asyncLlmTaskMapper,
            List<AsyncTaskExecutor> executors) {
        super(redisUtil);
        this.asyncLlmTaskMapper = asyncLlmTaskMapper;
        Map<Integer, AsyncTaskExecutor> map = new HashMap<>();
        for (AsyncTaskExecutor executor : executors) {
            Integer code = executor.type().getCode();
            AsyncTaskExecutor previous = map.put(code, executor);
            if (previous != null) {
                throw new IllegalStateException("重复的任务执行器: type=" + executor.type());
            }
        }
        this.executors = Map.copyOf(map);
    }

    @Override
    protected StreamClaim claimTask(Long taskId) {
        AsyncLlmTask task = asyncLlmTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        if (asyncLlmTaskMapper.claimPendingTask(taskId) != 1) {
            return null;
        }
        return new StreamClaim(task.getId(), task.getUserId(), task);
    }

    /**
     * 按类型查找执行器；未注册（含预留向量化）则失败，由模板标 FAILED。
     */
    @Override
    protected String processClaimed(StreamClaim claimed) {
        AsyncLlmTask task = (AsyncLlmTask) claimed.attachment();
        AsyncTaskType type = AsyncTaskType.fromCode(task.getTaskType());
        AsyncTaskExecutor executor = type == null ? null : executors.get(type.getCode());
        if (executor == null) {
            throw new IllegalStateException("未注册任务执行器: type=" + task.getTaskType());
        }
        return executor.execute(task);
    }

    @Override
    protected void markSuccess(long taskId, String result) {
        asyncLlmTaskMapper.markTaskSuccess(taskId, result);
    }

    @Override
    protected void markFailed(long taskId, String error) {
        asyncLlmTaskMapper.markTaskFailed(taskId, error);
    }

    @Override
    protected String streamKey() {
        return RedisConstants.LLM_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return RedisConstants.LLM_STREAM_GROUP;
    }

    @Override
    protected String name() {
        return "LLM";
    }

    @Override
    public void start() {
        startConsumer();
        running = true;
    }

    @Override
    public void stop() {
        stopConsumer();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
