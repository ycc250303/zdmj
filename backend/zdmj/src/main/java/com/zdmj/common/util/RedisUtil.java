package com.zdmj.common.util;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.constants.RedisConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 辅助：岗位等业务缓存。故障记日志并返回空结果，调用方可回源数据库。
 *
 * <p>禁止用于登录 allowlist、验证码、限流。登录态见 {@code com.zdmj.common.security.JwtSessionStore}；
 * 验证码与限流已直连 {@link StringRedisTemplate}。</p>
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
}
