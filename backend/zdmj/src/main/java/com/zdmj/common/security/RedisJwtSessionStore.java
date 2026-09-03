package com.zdmj.common.security;

import com.zdmj.common.constants.RedisConstants;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录 allowlist：{@code jwt:token:{userId}}，精确 TTL，无抖动。
 */
@Component
@RequiredArgsConstructor
public class RedisJwtSessionStore implements JwtSessionStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void replace(long userId, String token) {
        Objects.requireNonNull(token, "token");
        redisTemplate.opsForValue().set(key(userId), token, RedisConstants.JWT_TOKEN_TTL, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> find(long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void revoke(long userId) {
        redisTemplate.delete(key(userId));
    }

    private static String key(long userId) {
        return RedisConstants.JWT_TOKEN_KEY + userId;
    }
}
