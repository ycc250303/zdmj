package com.zdmj.common.util;

public class RedisConstants {
    // 验证码前缀
    public static final String VERIFICATION_CODE_KEY = "verification:code:";
    // 验证码过期时间（分钟）
    public static final int CODE_EXPIRE_TTL = 5 * 60;
}
