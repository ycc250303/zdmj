package com.zdmj.common.async;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;

import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.RedisUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Stream 消费模板：建组、启动排空本消费者 PEL、循环读新消息、claim、ACK。
 *
 * <p>失败一律 {@code FAILED + ACK}，不自动重入队。约定单实例；{@code consumerName} 固定，
 * 重启后靠偏移 {@code 0-0} 取回未 ACK 消息。{@code claim} 允许 PENDING 与 RUNNING。</p>
 */
@Slf4j
public abstract class AbstractStreamConsumer {

    private static final int ERROR_MAX_LEN = 500;

    protected final RedisUtil redisUtil;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private String consumerName;

    protected AbstractStreamConsumer(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /**
     * 建消费组（已存在则忽略），可补投递进行中任务，再单线程排空本 PEL 并阻塞 {@code XREADGROUP >}。
     */
    public void startConsumer() {
        this.consumerName = name().toLowerCase() + "-consumer";
        redisUtil.ensureConsumerGroup(streamKey(), groupName());
        replayOnStart();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "zdmj-" + name().toLowerCase() + "-stream-consumer");
            t.setDaemon(true);
            return t;
        });
        running.set(true);
        executorService.submit(this::consumeLoop);
        log.info("{}消费者已启动: consumerName={}（单实例）", name(), consumerName);
    }

    /** 停止循环并中断消费线程。 */
    public void stopConsumer() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
        }
        log.info("{}消费者已关闭: consumerName={}", name(), consumerName);
    }

    /**
     * 先排空本消费者未 ACK 消息，再阻塞读新消息。中断或 {@link #stopConsumer} 后退出。
     */
    private void consumeLoop() {
        drainOwnPending();
        Duration block = Duration.ofSeconds(RedisConstants.STREAM_BLOCK_SECONDS);
        while (running.get()) {
            try {
                List<MapRecord<String, String, String>> records = redisUtil.xreadGroup(
                        streamKey(), groupName(), consumerName, RedisConstants.STREAM_READ_COUNT, block,
                        ReadOffset.lastConsumed());
                for (MapRecord<String, String, String> record : records) {
                    consumeRecord(record);
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted() || !running.get()) {
                    break;
                }
                log.error("消费消息时发生错误: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 非阻塞读取本消费者 PEL（偏移 {@code 0-0}）直至空。重启后行可能已是 RUNNING，由 claim 接手。
     */
    private void drainOwnPending() {
        while (running.get()) {
            try {
                List<MapRecord<String, String, String>> records = redisUtil.xreadGroup(
                        streamKey(), groupName(), consumerName, RedisConstants.STREAM_READ_COUNT, Duration.ZERO,
                        ReadOffset.from(RedisConstants.STREAM_OWN_PENDING_OFFSET));
                if (records.isEmpty()) {
                    return;
                }
                for (MapRecord<String, String, String> record : records) {
                    consumeRecord(record);
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted() || !running.get()) {
                    return;
                }
                log.error("排空 PEL 失败，转入读新消息: {}", e.getMessage(), e);
                return;
            }
        }
    }

    /**
     * 单条消费（单测可直接调用，不启动循环）。
     *
     * <ol>
     *   <li>解析 {@code taskId}；缺失或非法则 ACK 丢弃。</li>
     *   <li>{@link #claimTask} 返回空 → ACK（行不存在或非进行中）。</li>
     *   <li>{@link #processClaimed} 成功 → SUCCESS + ACK；任何异常 → FAILED + ACK。</li>
     * </ol>
     *
     * @param record Redis Stream 记录，value 为标识 Map
     */
    protected void consumeRecord(MapRecord<String, String, String> record) {
        RecordId recordId = record.getId();
        Long taskId = parseTaskId(recordId, record.getValue());
        if (taskId == null) {
            ack(recordId);
            return;
        }

        StreamClaim claimed = claimTask(taskId);
        if (claimed == null) {
            log.debug("claim 跳过（不存在或非进行中）: taskId={}", taskId);
            ack(recordId);
            return;
        }

        UserHolder.set(UserContext.of(claimed.userId(), "async-task"));
        try {
            String result = processClaimed(claimed);
            markSuccess(claimed.taskId(), result);
            ack(recordId);
        } catch (Exception e) {
            log.error("处理{}失败: taskId={}", name(), claimed.taskId(), e);
            markFailed(claimed.taskId(), truncateError(e.getMessage()));
            ack(recordId);
        } finally {
            UserHolder.clear();
        }
    }

    /** {@code XACK}，从 PEL 移除。 */
    protected void ack(RecordId recordId) {
        redisUtil.xack(streamKey(), groupName(), recordId);
    }

    protected static String truncateError(String error) {
        if (error == null) {
            return "未知错误";
        }
        return error.length() > ERROR_MAX_LEN ? error.substring(0, ERROR_MAX_LEN) : error;
    }

    /**
     * 解析 Stream Map 中的 {@code taskId}；缺字段或非数字返回 {@code null}（调用方 ACK 丢弃）。
     */
    private Long parseTaskId(RecordId recordId, Map<String, String> data) {
        if (data == null) {
            log.warn("空消息体，丢弃: id={}", recordId);
            return null;
        }
        try {
            String taskId = data.get(RedisConstants.STREAM_FIELD_TASK_ID);
            if (taskId == null) {
                log.warn("消息缺 taskId，丢弃: id={}, data={}", recordId, data);
                return null;
            }
            return Long.parseLong(taskId);
        } catch (RuntimeException e) {
            log.warn("解析消息失败，丢弃: id={}", recordId, e);
            return null;
        }
    }

    /**
     * 启动消费循环前补投递（如向量任务表仍为 PENDING 但 Stream 无消息）。默认空操作。
     */
    protected void replayOnStart() {
        // LLM 任务靠 PEL；向量化另覆盖
    }

    /**
     * 加载任务并 claim。行不存在或非 PENDING/RUNNING 时返回 {@code null}（调用方 ACK）。
     *
     * @param taskId Stream 中的任务 id
     */
    protected abstract StreamClaim claimTask(Long taskId);

    /**
     * 域内同步执行。嵌套 LLM 在此方法内直接调 Service，禁止再 enqueue。
     *
     * @param claimed 已 claim 为 RUNNING 的上下文
     * @return 写入任务 result 的 JSON；无独立结果时返回 {@code null}
     */
    protected abstract String processClaimed(StreamClaim claimed);

    /** 业务成功后把任务行标 SUCCESS。 */
    protected abstract void markSuccess(long taskId, String result);

    /** 业务失败后把任务行标 FAILED。 */
    protected abstract void markFailed(long taskId, String error);

    /** 本消费者读取的 Stream key。 */
    protected abstract String streamKey();

    /** 消费组名，如 {@code zdmj:llm:group}。 */
    protected abstract String groupName();

    /** 日志、线程名与 {@code consumerName} 前缀，如 {@code LLM} → {@code llm-consumer}。 */
    protected abstract String name();
}
