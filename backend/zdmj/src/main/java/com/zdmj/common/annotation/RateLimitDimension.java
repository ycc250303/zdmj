package com.zdmj.common.annotation;

/**
 * 方法级限流的计数维度，名称用于生成 Redis 计数键。
 */
public enum RateLimitDimension {
    /** 同一方法全局共享计数。 */
    GLOBAL,
    /** 同一方法按客户端 IP 分别计数。 */
    IP,
    /** 同一方法按已认证用户编号分别计数。 */
    USER
}
