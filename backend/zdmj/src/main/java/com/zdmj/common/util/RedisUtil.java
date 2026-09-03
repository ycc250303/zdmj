package com.zdmj.common.util;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.constants.RedisConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 辅助：岗位等业务缓存（软失败）与 Stream/任务锁（硬失败）。
 *
 * <p><b>禁止</b>用于登录 allowlist、验证码、限流。登录态见 {@code com.zdmj.common.security.JwtSessionStore}；
 * 验证码与限流已直连 {@link StringRedisTemplate}。</p>
 *
 * <ul>
 *   <li>缓存 API（{@code get}/{@code set}/{@code setString} 等）：故障记日志并返回 {@code null}/{@code false}，调用方可回源 DB。</li>
 *   <li>Stream / 任务锁（{@link #tryLock} 起）：失败上抛，由调用方回退。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final Double RANDOM_OFFSET = 0.05;

    /**
     * 计算带随机偏移的过期时间（偏移量为原时间的 0–5%）
     *
     * @param baseSeconds 基础过期时间（秒）
     * @return 带随机偏移的过期时间（秒）
     */
    private long calculateExpireTimeWithRandom(int baseSeconds) {
        int offset = (int) (baseSeconds * RANDOM_OFFSET);
        int randomOffset = random.nextInt(offset + 1);
        return baseSeconds + randomOffset;
    }

    /**
     * 设置缓存（自动添加随机偏移）
     * 
     * @param key           缓存键
     * @param value         缓存值（对象会被序列化为JSON）
     * @param expireSeconds 基础过期时间，单位为秒，会自动添加 0–5% 的随机偏移
     */
    public void set(String key, Object value, int expireSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            long actualExpireSeconds = calculateExpireTimeWithRandom(expireSeconds);
            redisTemplate.opsForValue().set(key, jsonValue, actualExpireSeconds, TimeUnit.SECONDS);
            log.debug("设置缓存成功: key={}, expire={}秒", key, actualExpireSeconds);
        } catch (org.springframework.dao.QueryTimeoutException | io.lettuce.core.RedisCommandTimeoutException e) {
            log.warn("设置缓存超时（不影响主流程）: key={}, error={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("设置缓存失败（不影响主流程）: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 设置缓存（字符串值，不进行JSON序列化）
     * 
     * @param key           缓存键
     * @param value         缓存值（字符串）
     * @param expireSeconds 基础过期时间，单位为秒，会自动添加 0–5% 的随机偏移
     */
    public void setString(String key, String value, int expireSeconds) {
        try {
            long actualExpireSeconds = calculateExpireTimeWithRandom(expireSeconds);
            redisTemplate.opsForValue().set(key, value, actualExpireSeconds, TimeUnit.SECONDS);
            log.debug("设置缓存成功: key={}, expire={}秒", key, actualExpireSeconds);
        } catch (org.springframework.dao.QueryTimeoutException | io.lettuce.core.RedisCommandTimeoutException e) {
            log.warn("设置缓存超时（不影响主流程）: key={}, error={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("设置缓存失败（不影响主流程）: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取缓存（自动反序列化为指定类型）
     * 
     * @param key   缓存键
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 缓存对象，如果不存在或反序列化失败返回null
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            String jsonValue = redisTemplate.opsForValue().get(key);
            if (jsonValue == null) {
                return null;
            }
            log.debug("获取缓存成功: key={}", key);
            return objectMapper.readValue(jsonValue, clazz);
        } catch (org.springframework.dao.QueryTimeoutException | io.lettuce.core.RedisCommandTimeoutException e) {
            log.warn("获取缓存超时（不影响主流程）: key={}, error={}", key, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("获取缓存失败（不影响主流程）: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取缓存字符串值
     * 
     * @param key 缓存键
     * @return 缓存值，如果不存在返回null
     */
    public String getString(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (org.springframework.dao.QueryTimeoutException | io.lettuce.core.RedisCommandTimeoutException e) {
            log.warn("获取缓存超时（不影响主流程）: key={}, error={}", key, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("获取缓存失败（不影响主流程）: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存
     * 
     * @param key 缓存键
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("删除缓存成功: key={}", key);
        } catch (org.springframework.dao.QueryTimeoutException | io.lettuce.core.RedisCommandTimeoutException e) {
            log.warn("删除缓存超时（不影响主流程）: key={}, error={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("删除缓存失败（不影响主流程）: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 检查缓存是否存在
     * 
     * @param key 缓存键
     * @return true表示存在，false表示不存在
     */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (org.springframework.dao.QueryTimeoutException | io.lettuce.core.RedisCommandTimeoutException e) {
            log.warn("检查缓存是否存在超时（不影响主流程）: key={}, error={}", key, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("检查缓存是否存在失败（不影响主流程）: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 设置空值标记（防止缓存穿透，仅在复杂查询中使用）
     * 
     * @param key           缓存键
     * @param expireSeconds 过期时间（秒）
     */
    public void setNullValue(String key, int expireSeconds) {
        String nullKey = RedisConstants.NULL_VALUE_KEY + key;
        setString(nullKey, "1", expireSeconds);
    }

    /**
     * 检查是否存在空值标记
     * 
     * @param key 缓存键
     * @return true表示存在空值标记，false表示不存在
     */
    public boolean isNullValue(String key) {
        String nullKey = RedisConstants.NULL_VALUE_KEY + key;
        return exists(nullKey);
    }

    /**
     * 删除空值标记
     * 
     * @param key 缓存键
     */
    public void deleteNullValue(String key) {
        String nullKey = RedisConstants.NULL_VALUE_KEY + key;
        delete(nullKey);
    }

    // ========== Redis Stream 消息队列（失败上抛，与上方缓存吞异常不同）==========

    /**
     * 抢占任务锁：{@code SET key value NX EX}。未抢到返回 {@code false}；Redis 异常上抛，由调用方回退到 DB 唯一索引。
     */
    public boolean tryLock(String key, String value, long expireSeconds) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS);
        boolean ok = Boolean.TRUE.equals(acquired);
        log.debug("任务锁 SET NX: key={}, acquired={}", key, ok);
        return ok;
    }

    /**
     * 释放任务锁。消费者 SUCCESS/FAILED 时调用；进程宕机靠 TTL。
     */
    public void unlock(String key) {
        redisTemplate.delete(key);
        log.debug("释放任务锁: key={}", key);
    }

    /**
     * {@code XADD} 并按 {@link RedisConstants#STREAM_MAXLEN} 近似裁剪。
     *
     * @return 消息 ID
     */
    public RecordId xadd(String streamKey, Map<String, String> fields) {
        return xadd(streamKey, fields, RedisConstants.STREAM_MAXLEN);
    }

    /**
     * {@code XADD MAXLEN ~ maxlen}。
     */
    public RecordId xadd(String streamKey, Map<String, String> fields, long maxlen) {
        XAddOptions options = XAddOptions.maxlen(maxlen).approximateTrimming(true);
        RecordId recordId = streamOps().add(streamKey, fields, options);
        if (recordId == null) {
            throw new IllegalStateException("XADD 返回空 recordId: stream=" + streamKey);
        }
        log.debug("XADD: stream={}, id={}, fields={}", streamKey, recordId, fields);
        return recordId;
    }

    /**
     * 写入异步任务消息（仅标识字段，payload 在 DB）。
     */
    public RecordId xaddTask(String streamKey, long taskId, int taskType, long userId, String bizKey, int retryCount) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(RedisConstants.STREAM_FIELD_TASK_ID, Long.toString(taskId));
        fields.put(RedisConstants.STREAM_FIELD_TYPE, Integer.toString(taskType));
        fields.put(RedisConstants.STREAM_FIELD_USER_ID, Long.toString(userId));
        fields.put(RedisConstants.STREAM_FIELD_BIZ_KEY, bizKey);
        fields.put(RedisConstants.STREAM_FIELD_RETRY_COUNT, Integer.toString(retryCount));
        return xadd(streamKey, fields);
    }

    /**
     * 确保消费组存在（{@code XGROUP CREATE ... MKSTREAM}）。组已存在时忽略 BUSYGROUP。
     */
    public void ensureConsumerGroup(String streamKey, String group) {
        try {
            streamOps().createGroup(streamKey, ReadOffset.from("0-0"), group);
            log.info("创建 Stream 消费组: stream={}, group={}", streamKey, group);
        } catch (RuntimeException e) {
            if (isBusyGroup(e)) {
                log.debug("消费组已存在: stream={}, group={}", streamKey, group);
                return;
            }
            log.error("创建消费组失败: stream={}, group={}", streamKey, group, e);
            throw e;
        }
    }

    /**
     * {@code XREADGROUP}，读取本消费者尚未投递的新消息（{@code >}）。
     */
    public List<MapRecord<String, String, String>> xreadGroup(String streamKey, String group, String consumerName,
            int count, Duration block) {
        StreamReadOptions options = StreamReadOptions.empty().count(count);
        if (block != null && !block.isZero() && !block.isNegative()) {
            options = options.block(block);
        }
        List<MapRecord<String, String, String>> records = streamOps().read(
                Consumer.from(group, consumerName),
                options,
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        return records == null ? List.of() : records;
    }

    /**
     * {@code XACK}，从 PEL 移除已处理消息。
     */
    public long xack(String streamKey, String group, String... recordIds) {
        if (recordIds == null || recordIds.length == 0) {
            return 0L;
        }
        Long acked = streamOps().acknowledge(streamKey, group, recordIds);
        long n = acked == null ? 0L : acked;
        log.debug("XACK: stream={}, group={}, ids={}, acked={}", streamKey, group, recordIds, n);
        return n;
    }

    public long xack(String streamKey, String group, RecordId recordId) {
        if (recordId == null) {
            return 0L;
        }
        return xack(streamKey, group, recordId.getValue());
    }

    /**
     * {@code XPENDING}，查看组内 PEL。
     */
    public PendingMessages xpending(String streamKey, String group, long count) {
        return streamOps().pending(streamKey, group, Range.unbounded(), count);
    }

    /**
     * {@code XCLAIM}，接管空闲超过 {@code minIdle} 的 PEL 消息。
     */
    public List<MapRecord<String, String, String>> xclaim(String streamKey, String group, String consumerName,
            Duration minIdle, RecordId... recordIds) {
        if (recordIds == null || recordIds.length == 0) {
            return List.of();
        }
        List<MapRecord<String, String, String>> claimed = streamOps().claim(streamKey, group, consumerName, minIdle,
                recordIds);
        return claimed == null ? List.of() : claimed;
    }

    @SuppressWarnings("unchecked")
    private StreamOperations<String, String, String> streamOps() {
        return redisTemplate.opsForStream();
    }

    private static boolean isBusyGroup(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
