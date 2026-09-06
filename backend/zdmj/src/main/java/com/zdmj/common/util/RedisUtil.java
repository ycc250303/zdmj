package com.zdmj.common.util;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
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
 * Redis 辅助：岗位等业务缓存（软失败）与 Stream（硬失败）。
 *
 * <p><b>禁止</b>用于登录 allowlist、验证码、限流。登录态见 {@code com.zdmj.common.security.JwtSessionStore}；
 * 验证码与限流已直连 {@link StringRedisTemplate}。</p>
 *
 * <ul>
 *   <li>缓存 API（{@code get}/{@code set}/{@code setString} 等）：故障记日志并返回 {@code null}/{@code false}，调用方可回源 DB。</li>
 *   <li>Stream API（{@link #xadd} 起）：失败上抛，由调用方回退。</li>
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
     * 给 TTL 加 0–5% 随机偏移，减轻缓存雪崩。
     *
     * @param baseSeconds 基础过期秒数
     * @return 实际过期秒数（≥ {@code baseSeconds}）
     */
    private long calculateExpireTimeWithRandom(int baseSeconds) {
        int offset = (int) (baseSeconds * RANDOM_OFFSET);
        int randomOffset = random.nextInt(offset + 1);
        return baseSeconds + randomOffset;
    }

    /**
     * 将对象序列化为 JSON 写入缓存（TTL 带随机偏移）；Redis 故障只记日志。
     *
     * @param key           缓存键
     * @param value         待序列化对象
     * @param expireSeconds 基础过期秒数
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
     * 原样写入字符串缓存（TTL 带随机偏移）；Redis 故障只记日志。
     *
     * @param key           缓存键
     * @param value         字符串值，不经 JSON 序列化
     * @param expireSeconds 基础过期秒数
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
     * 读取 JSON 缓存并反序列化为指定类型；缺失或故障返回 {@code null}。
     *
     * @param key   缓存键
     * @param clazz 目标类型
     * @param <T>   反序列化结果类型
     * @return 缓存对象；键不存在、超时或反序列化失败时为 {@code null}
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
     * 读取字符串缓存；缺失或故障返回 {@code null}。
     *
     * @param key 缓存键
     * @return 缓存字符串；键不存在或 Redis 故障时为 {@code null}
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
     * 删除指定缓存键；Redis 故障只记日志。
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
     * 判断缓存键是否存在；故障视为不存在。
     *
     * @param key 缓存键
     * @return {@code true} 键存在；不存在或 Redis 故障为 {@code false}
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
     * 写入空值标记，防止缓存穿透（仅复杂查询使用）。
     *
     * @param key           业务缓存键（内部会加空值前缀）
     * @param expireSeconds 标记过期秒数
     */
    public void setNullValue(String key, int expireSeconds) {
        String nullKey = RedisConstants.NULL_VALUE_KEY + key;
        setString(nullKey, "1", expireSeconds);
    }

    /**
     * 判断是否已有空值标记。
     *
     * @param key 业务缓存键（内部会加空值前缀）
     * @return {@code true} 存在空值标记，查询应直接当空结果
     */
    public boolean isNullValue(String key) {
        String nullKey = RedisConstants.NULL_VALUE_KEY + key;
        return exists(nullKey);
    }

    /**
     * 删除空值标记，通常在回源写入真实缓存后调用。
     *
     * @param key 业务缓存键（内部会加空值前缀）
     */
    public void deleteNullValue(String key) {
        String nullKey = RedisConstants.NULL_VALUE_KEY + key;
        delete(nullKey);
    }

    // ========== Redis Stream 消息队列 ==========

    /**
     * {@code XADD} 写入 Stream，并按 {@link RedisConstants#STREAM_MAXLEN} 近似裁剪。
     *
     * @param streamKey Stream 键，如 {@code zdmj:llm:stream}
     * @param fields    消息字段（字符串 Map）
     * @return 新消息 ID
     */
    public RecordId xadd(String streamKey, Map<String, String> fields) {
        XAddOptions options = XAddOptions.maxlen(RedisConstants.STREAM_MAXLEN).approximateTrimming(true);
        RecordId recordId = streamOps().add(streamKey, fields, options);
        if (recordId == null) {
            throw new IllegalStateException("XADD 返回空 recordId: stream=" + streamKey);
        }
        log.debug("XADD: stream={}, id={}, fields={}", streamKey, recordId, fields);
        return recordId;
    }

    /**
     * 写入异步任务消息（仅 {@code taskId}，payload 在 DB）。
     *
     * @param streamKey Stream 键
     * @param taskId    {@code async_llm_tasks.id}
     * @return 新消息 ID
     */
    public RecordId xaddTask(String streamKey, long taskId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(RedisConstants.STREAM_FIELD_TASK_ID, Long.toString(taskId));
        return xadd(streamKey, fields);
    }

    /**
     * 创建消费组（{@code XGROUP CREATE ... MKSTREAM}）；组已存在则忽略 BUSYGROUP。
     *
     * @param streamKey Stream 键，不存在时会一并创建
     * @param group     消费组名
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
     * {@code XREADGROUP}。偏移 {@code >}（{@link ReadOffset#lastConsumed()}）读新消息；
     * {@code 0-0} 读本消费者未 ACK 的 PEL。
     *
     * @param streamKey    Stream 键
     * @param group        消费组名
     * @param consumerName 本实例消费者名（单实例固定名）
     * @param count        单次最多条数
     * @param block        阻塞等待时长；{@code null}/零/负则立即返回
     * @param offset       读偏移；{@code null} 视为 {@code >}
     * @return 消息列表，无消息时为空列表（非 {@code null}）
     */
    public List<MapRecord<String, String, String>> xreadGroup(String streamKey, String group, String consumerName,
            int count, Duration block, ReadOffset offset) {
        StreamReadOptions options = StreamReadOptions.empty().count(count);
        if (block != null && !block.isZero() && !block.isNegative()) {
            options = options.block(block);
        }
        ReadOffset readOffset = offset == null ? ReadOffset.lastConsumed() : offset;
        List<MapRecord<String, String, String>> records = streamOps().read(
                Consumer.from(group, consumerName),
                options,
                StreamOffset.create(streamKey, readOffset));
        return records == null ? List.of() : records;
    }

    /**
     * {@code XACK} 确认已处理消息，并从 PEL 移除。
     *
     * @param streamKey Stream 键
     * @param group     消费组名
     * @param recordIds 待确认消息 ID，可空
     * @return 实际 ACK 条数；{@code recordIds} 为空时为 0
     */
    public long xack(String streamKey, String group, RecordId... recordIds) {
        if (recordIds == null || recordIds.length == 0) {
            return 0L;
        }
        Long acked = streamOps().acknowledge(streamKey, group, recordIds);
        long n = acked == null ? 0L : acked;
        log.debug("XACK: stream={}, group={}, ids={}, acked={}", streamKey, group, recordIds, n);
        return n;
    }

    /** 取得 Redis Stream 操作句柄。 */
    @SuppressWarnings("unchecked")
    private StreamOperations<String, String, String> streamOps() {
        return redisTemplate.opsForStream();
    }

    /**
     * 判断异常链是否为消费组已存在（BUSYGROUP）。
     *
     * @param error {@code XGROUP CREATE} 抛出的异常
     * @return {@code true} 组已存在，调用方可忽略
     */
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
