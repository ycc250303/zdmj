package com.zdmj.common.aspect;

import java.lang.reflect.Method;
import java.util.Collections;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String RATE_LIMIT_SCRIPT_PATH = "lua/rate_limit.lua";
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = loadRateLimitScript();

    private final StringRedisTemplate redisTemplate;

    private static RedisScript<Long> loadRateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(RATE_LIMIT_SCRIPT_PATH));
        script.setResultType(Long.class);
        return script;
    }

    @Around("@annotation(com.zdmj.common.annotation.RateLimit) || "
            + "@annotation(com.zdmj.common.annotation.RateLimit.List)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        for (RateLimit rule : method.getAnnotationsByType(RateLimit.class)) {
            if (!tryAcquire(className, methodName, rule)) {
                log.debug("限流触发: {}.{} dimension={}", className, methodName, rule.dimension());
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        }
        return joinPoint.proceed();
    }

    private boolean tryAcquire(String className, String methodName, RateLimit rule) {
        long windowMs = rule.timeUnit().toMillis(rule.interval());
        String key = generateKey(className, methodName, rule.dimension());

        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(windowMs),
                String.valueOf((long) rule.count()));
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 构建限流 Redis Key。
     * hash tag {@code {Class:method}} 便于 Redis Cluster 同 slot 路由。
     */
    private String generateKey(String className, String methodName, RateLimit.Dimension dimension) {
        String suffix = switch (dimension) {
            case GLOBAL -> null;
            case IP -> getClientIp();
            case USER -> getCurrentUserId();
        };
        String hashTag = "{" + className + ":" + methodName + "}";
        String key = RATE_LIMIT_KEY_PREFIX + hashTag + ":" + dimension.name().toLowerCase();
        if (suffix != null && !suffix.isEmpty()) {
            key += ":" + suffix;
        }
        return key;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    private String getCurrentUserId() {
        Long userId = UserHolder.getUserId();
        return userId != null ? userId.toString() : "anonymous";
    }
}
