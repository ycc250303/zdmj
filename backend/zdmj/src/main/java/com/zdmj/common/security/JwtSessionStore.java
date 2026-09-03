package com.zdmj.common.security;

import java.util.Optional;

/**
 * JWT 登录 allowlist。Redis IO/超时必须上抛，禁止把故障当成 miss。
 *
 * <p>实现见 {@link RedisJwtSessionStore}；勿使用 {@code RedisUtil} 缓存 API。</p>
 */
public interface JwtSessionStore {

    /**
     * SET 覆盖当前用户 token。TTL 与 JWT 过期一致，不加随机偏移。
     */
    void replace(long userId, String token);

    /**
     * miss → empty；连接失败或超时必须抛异常，禁止 empty 冒充 miss。
     */
    Optional<String> find(long userId);

    /**
     * DEL 登录态（预留登出）。
     */
    void revoke(long userId);
}
