package com.zdmj.common.security;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录 allowlist：{@code jwt:token:{userId}}，精确 TTL，无抖动。
 * 有效期 7 天，与 {@link JwtTokenService} 的令牌有效期一致。
 */
@Component
@RequiredArgsConstructor
public class RedisJwtSessionStore implements JwtSessionStore {

    private static final String TOKEN_KEY_PREFIX = "jwt:token:";

    /** 登录态有效期，单位秒。与 {@link JwtTokenService} 的 7 天有效期一致，不加随机偏移。 */
    private static final long TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void replace(long userId, String token) {
        Objects.requireNonNull(token, "token");
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + userId, token, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> find(long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId));
    }

    @Override
    public void revoke(long userId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + userId);
    }
}
