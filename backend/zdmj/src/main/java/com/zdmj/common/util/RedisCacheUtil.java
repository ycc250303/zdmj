package com.zdmj.common.util;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisCacheUtil {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final Double RANDOM_OFFSET = 0.05;

    public RedisCacheUtil(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 计算带随机偏移的过期时间（偏移量为原时间的10%）
     * 
     * @param baseSeconds 基础过期时间（秒）
     * @return 带随机偏移的过期时间（秒）
     */
    private long calculateExpireTimeWithRandom(int baseSeconds) {
        // 计算10%的偏移量
        int offset = (int) (baseSeconds * RANDOM_OFFSET);
        // 生成0到offset之间的随机数
        int randomOffset = random.nextInt(offset + 1);
        return baseSeconds + randomOffset;
    }

    /**
     * 设置缓存（自动添加随机偏移）
     * 
     * @param key           缓存键
     * @param value         缓存值（对象会被序列化为JSON）
     * @param expireSeconds 基础过期时间，单位为秒，会自动添加10%的随机偏移
     */
    public void set(String key, Object value, int expireSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            long actualExpireSeconds = calculateExpireTimeWithRandom(expireSeconds);
            redisTemplate.opsForValue().set(key, jsonValue, actualExpireSeconds, TimeUnit.SECONDS);
            log.debug("设置缓存成功: key={}, expire={}秒", key, actualExpireSeconds);
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
            // 不抛出异常，避免影响主业务流程
        }
    }

    /**
     * 设置缓存（字符串值，不进行JSON序列化）
     * 
     * @param key           缓存键
     * @param value         缓存值（字符串）
     * @param expireSeconds 基础过期时间，单位为秒，会自动添加10%的随机偏移
     */
    public void setString(String key, String value, int expireSeconds) {
        try {
            long actualExpireSeconds = calculateExpireTimeWithRandom(expireSeconds);
            redisTemplate.opsForValue().set(key, value, actualExpireSeconds, TimeUnit.SECONDS);
            log.debug("设置缓存成功: key={}, expire={}秒", key, actualExpireSeconds);
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
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
            return objectMapper.readValue(jsonValue, clazz);
        } catch (Exception e) {
            log.error("获取缓存失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 获取缓存（支持复杂类型，如List、Map等）
     * 
     * @param key           缓存键
     * @param typeReference 类型引用（用于处理泛型）
     * @param <T>           泛型类型
     * @return 缓存对象，如果不存在或反序列化失败返回null
     */
    public <T> T get(String key, TypeReference<T> typeReference) {
        try {
            String jsonValue = redisTemplate.opsForValue().get(key);
            if (jsonValue == null) {
                return null;
            }
            return objectMapper.readValue(jsonValue, typeReference);
        } catch (Exception e) {
            log.error("获取缓存失败: key={}", key, e);
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
        } catch (Exception e) {
            log.error("获取缓存失败: key={}", key, e);
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
        } catch (Exception e) {
            log.error("删除缓存失败: key={}", key, e);
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
        } catch (Exception e) {
            log.error("检查缓存是否存在失败: key={}", key, e);
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
}
