package com.zdmj.common.async;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;

/**
 * LLM Stream 消费者。一期不接域 execute，收到消息标 FAILED，避免空跑 SUCCESS。
 */
@Component
@ConditionalOnProperty(prefix = "zdmj.async.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmStreamConsumer extends AbstractStreamConsumer implements SmartLifecycle {

    private volatile boolean running;

    public LlmStreamConsumer(RedisUtil redisUtil, AsyncLlmTaskMapper asyncLlmTaskMapper) {
        super(redisUtil, asyncLlmTaskMapper);
    }

    /**
     * 一期占位：不调域 execute，抛异常标 FAILED，避免空跑 SUCCESS。
     */
    @Override
    protected String processBusiness(AsyncLlmTask task) {
        throw new IllegalStateException("一期未接入业务执行器: type=" + task.getTaskType());
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
