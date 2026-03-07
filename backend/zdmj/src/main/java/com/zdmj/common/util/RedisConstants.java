package com.zdmj.common.util;

public class RedisConstants {
    // 验证码前缀
    public static final String VERIFICATION_CODE_KEY = "verification:code:";
    // 验证码过期时间（分钟）
    public static final int CODE_EXPIRE_TTL = 5 * 60;

    // 会话缓存前缀 
    public static final String CONVERSATION_KEY = "conversation:";
    // 会话缓存过期时间（秒）
    public static final int CONVERSATION_TTL = 3600;

    // 会话消息缓存前缀
    public static final String CONVERSATION_MESSAGES_KEY = "conversation:messages:";
    // 会话消息缓存过期时间（秒）
    public static final int CONVERSATION_MESSAGES_TTL = 3600;

    // 空值标记前缀（用于防止缓存穿透）
    public static final String NULL_VALUE_KEY = "null:value:";
}
