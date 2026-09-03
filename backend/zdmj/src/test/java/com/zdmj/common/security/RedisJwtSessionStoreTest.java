package com.zdmj.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zdmj.common.constants.RedisConstants;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisJwtSessionStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisJwtSessionStore store;

    @BeforeEach
    void setUp() {
        store = new RedisJwtSessionStore(redisTemplate);
    }

    @Test
    void replace_shouldSetExactTtlWithoutJitter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.replace(42L, "jwt-token");

        verify(valueOps).set(
                RedisConstants.JWT_TOKEN_KEY + 42L,
                "jwt-token",
                RedisConstants.JWT_TOKEN_TTL,
                TimeUnit.SECONDS);
    }

    @Test
    void find_whenMissing_shouldReturnEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(RedisConstants.JWT_TOKEN_KEY + 1L)).thenReturn(null);

        Optional<String> found = store.find(1L);

        assertTrue(found.isEmpty());
    }

    @Test
    void find_whenPresent_shouldReturnToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(RedisConstants.JWT_TOKEN_KEY + 1L)).thenReturn("stored");

        assertEquals("stored", store.find(1L).orElseThrow());
    }

    @Test
    void find_whenRedisFails_shouldPropagateNotEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThrows(RedisConnectionFailureException.class, () -> store.find(1L));
    }

    @Test
    void replace_whenRedisFails_shouldPropagate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertThrows(RedisConnectionFailureException.class, () -> store.replace(1L, "tok"));
    }

    @Test
    void revoke_shouldDeleteKey() {
        store.revoke(7L);

        verify(redisTemplate).delete(RedisConstants.JWT_TOKEN_KEY + 7L);
        verify(redisTemplate, never()).opsForValue();
    }
}
