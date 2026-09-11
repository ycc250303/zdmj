package com.zdmj.knowledgeService.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.async.AbstractStreamConsumer;
import com.zdmj.common.async.StreamClaim;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import lombok.extern.slf4j.Slf4j;

/**
 * 向量化 Stream 消费者：claim {@code knowledge_vector_tasks} 后调现有 embedding 逻辑。不写 {@code async_llm_tasks}。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "zdmj.async.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmbedStreamConsumer extends AbstractStreamConsumer implements SmartLifecycle {

    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final EmbedStreamProducer embedStreamProducer;

    private volatile boolean running;

    public EmbedStreamConsumer(RedisUtil redisUtil, KnowledgeVectorTaskMapper knowledgeVectorTaskMapper,
            KnowledgeEmbeddingService knowledgeEmbeddingService, EmbedStreamProducer embedStreamProducer) {
        super(redisUtil);
        this.knowledgeVectorTaskMapper = knowledgeVectorTaskMapper;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.embedStreamProducer = embedStreamProducer;
    }

    /**
     * 启动时把仍为 PENDING/RUNNING 的向量任务补 XADD，覆盖 INSERT 后崩溃或旧 {@code @Async} 残留。
     */
    @Override
    protected void replayOnStart() {
        var inflight = knowledgeVectorTaskMapper.selectList(new LambdaQueryWrapper<KnowledgeVectorTask>()
                .in(KnowledgeVectorTask::getStatus,
                        KnowledgeVectorTaskStatusEnum.PENDING.getCode(),
                        KnowledgeVectorTaskStatusEnum.RUNNING.getCode()));
        for (KnowledgeVectorTask task : inflight) {
            if (task.getId() != null) {
                embedStreamProducer.send(task.getId());
            }
        }
        log.info("向量任务启动补投递: count={}", inflight.size());
    }

    @Override
    protected StreamClaim claimTask(Long taskId) {
        KnowledgeVectorTask task = knowledgeVectorTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        if (knowledgeVectorTaskMapper.claimPendingTask(taskId) != 1) {
            return null;
        }
        return new StreamClaim(task.getId(), task.getUserId(), task);
    }

    @Override
    protected String processClaimed(StreamClaim claimed) {
        knowledgeEmbeddingService.executeClaimed((KnowledgeVectorTask) claimed.attachment());
        return null;
    }

    @Override
    protected void markSuccess(long taskId, String result) {
        knowledgeVectorTaskMapper.markTaskSuccess(taskId);
    }

    @Override
    protected void markFailed(long taskId, String error) {
        knowledgeVectorTaskMapper.markTaskFailed(taskId, error);
    }

    @Override
    protected String streamKey() {
        return RedisConstants.EMBED_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return RedisConstants.EMBED_STREAM_GROUP;
    }

    @Override
    protected String name() {
        return "EMBED";
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
