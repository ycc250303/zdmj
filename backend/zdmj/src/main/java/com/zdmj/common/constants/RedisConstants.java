package com.zdmj.common.constants;

public class RedisConstants {
    // 空值标记前缀（用于防止缓存穿透）
    public static final String NULL_VALUE_KEY = "null:value:";

    /** JWT 登录 allowlist 前缀（见 docs/backend/jwt-session.md）；勿走 RedisUtil 缓存 API */
    public static final String JWT_TOKEN_KEY = "jwt:token:";
    /** JWT 登录态 TTL（秒），须与 {@link com.zdmj.common.security.JwtTokenService} 过期时间一致，且不加随机偏移 */
    public static final int JWT_TOKEN_TTL = 7 * 24 * 60 * 60;

    // 岗位详情缓存前缀
    public static final String JOB_DETAIL_KEY = "job:detail:";
    // 岗位详情缓存过期时间（秒）- 10 分钟
    public static final int JOB_DETAIL_TTL = 10 * 60;
    // 岗位不存在时的空值标记 TTL（秒）
    public static final int JOB_DETAIL_NULL_TTL = 60;

}
