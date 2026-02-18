package com.zdmj.common.util;

public class RedisConstants {
    // 验证码前缀
    public static final String VERIFICATION_CODE_KEY = "verification:code:";
    // 验证码过期时间（分钟）
    public static final int CODE_EXPIRE_TTL = 5 * 60;
    
    // 空值标记前缀（用于防止缓存穿透）
    public static final String NULL_VALUE_KEY = "null:value:";
}
