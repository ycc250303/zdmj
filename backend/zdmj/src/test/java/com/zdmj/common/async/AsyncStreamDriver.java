package com.zdmj.common.async;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;

import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;

/**
 * 在集成测试中驱动一条 LLM Stream 消息。
 *
 * <p>测试配置关闭消费循环。本类用独立读者名读取一条新消息，再调用
 * {@link AbstractStreamConsumer#consumeRecord}。不启动后台线程。</p>
 */
public final class AsyncStreamDriver {

    /** 测试读者名，只用于本类的 {@code XREADGROUP}。 */
    static final String READER = "it-reader";

    private AsyncStreamDriver() {
    }

    /**
     * 确保消费组存在，并读取一条尚未投递给本读者的消息。
     *
     * @param redisUtil 真实 Redis Stream 客户端
     * @return 一条 Map 消息
     */
    public static MapRecord<String, String, String> readNext(RedisUtil redisUtil) {
        redisUtil.ensureConsumerGroup(RedisConstants.LLM_STREAM_KEY, RedisConstants.LLM_STREAM_GROUP);
        List<MapRecord<String, String, String>> records = redisUtil.xreadGroup(
                RedisConstants.LLM_STREAM_KEY,
                RedisConstants.LLM_STREAM_GROUP,
                READER,
                RedisConstants.STREAM_READ_COUNT,
                Duration.ZERO,
                ReadOffset.lastConsumed());
        if (records.size() != 1) {
            throw new IllegalStateException("期望读到 1 条 Stream 消息，实际 " + records.size());
        }
        return records.get(0);
    }

    /**
     * 消费一条已经读出的消息，包含抢占、执行和确认。
     *
     * @param consumer 手动构造的消费者，未启动循环
     * @param record   {@link #readNext} 的返回值，或测试自行写入的消息
     */
    public static void consume(LlmStreamConsumer consumer, MapRecord<String, String, String> record) {
        consumer.consumeRecord(record);
    }
}
