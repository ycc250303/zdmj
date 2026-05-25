package com.zdmj.common.aspect;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private RateLimitAspect aspect;

    @RateLimit(count = 2)
    void singleRuleMethod() {
    }

    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 1)
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 1)
    void multiRuleMethod() {
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
    }

    @Test
    void shouldProceedWhenAllowed() throws Throwable {
        Method method = RateLimitAspectTest.class.getDeclaredMethod("singleRuleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertEquals("ok", result);
        verify(joinPoint).proceed();
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(List.class), any(), any());
    }

    @Test
    void shouldThrowWhenLimited() throws Throwable {
        Method method = RateLimitAspectTest.class.getDeclaredMethod("singleRuleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> aspect.around(joinPoint));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), ex.getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void shouldRejectWhenAnyMultiRuleFails() throws Throwable {
        Method method = RateLimitAspectTest.class.getDeclaredMethod("multiRuleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(1L, 0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> aspect.around(joinPoint));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), ex.getCode());
        verify(joinPoint, never()).proceed();
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), any(List.class), any(), any());
    }

    @Test
    void shouldProceedWhenAllMultiRulesPass() throws Throwable {
        Method method = RateLimitAspectTest.class.getDeclaredMethod("multiRuleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(1L, 1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertEquals("ok", result);
        verify(joinPoint).proceed();
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), any(List.class), any(), any());
    }
}
