package com.zdmj.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(redisTemplate, new ObjectMapper());
    }

    @Test
    void setAndGet_jsonRoundTrip_shouldReturnObject() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("job:1")).thenReturn("{\"name\":\"后端\"}");

        redisUtil.set("job:1", java.util.Map.of("name", "后端"), 30);
        assertEquals("后端", redisUtil.get("job:1", java.util.Map.class).get("name"));
    }

    @Test
    void get_missingOrBroken_shouldReturnNull() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("missing")).thenReturn(null);
        when(values.get("broken")).thenReturn("{");

        assertNull(redisUtil.get("missing", String.class));
        assertNull(redisUtil.get("broken", String.class));
    }

    @Test
    void stringCache_failure_shouldNotEscape() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("down"));
        when(redisTemplate.hasKey("null:value:job:1")).thenThrow(new IllegalStateException("down"));
        when(redisTemplate.delete("null:value:job:1")).thenThrow(new IllegalStateException("down"));

        redisUtil.setString("job:1", "1", 10);
        assertNull(redisUtil.getString("job:1"));
        assertFalse(redisUtil.exists("job:1"));
        redisUtil.setNullValue("job:1", 10);
        assertFalse(redisUtil.isNullValue("job:1"));
        redisUtil.delete("job:1");
        redisUtil.deleteNullValue("job:1");
    }
}
