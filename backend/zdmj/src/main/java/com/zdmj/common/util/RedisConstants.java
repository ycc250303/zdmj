package com.zdmj.common.util;

public class RedisConstants {
    // 验证码前缀
    public static final String VERIFICATION_CODE_KEY = "verification:code:";
    // 验证码过期时间（分钟）
    public static final int CODE_EXPIRE_TTL = 5 * 60;

    // 会话缓存前缀
    public static final String CONVERSATION_KEY = "conversation:";
    // 会话缓存过期时间（秒）
    public static final int CONVERSATION_TTL = 60 * 60;

    // 会话消息缓存前缀
    public static final String CONVERSATION_MESSAGES_KEY = "conversation:messages:";
    // 会话消息缓存过期时间（秒）
    public static final int CONVERSATION_MESSAGES_TTL = 60 * 60;

    // 空值标记前缀（用于防止缓存穿透）
    public static final String NULL_VALUE_KEY = "null:value:";

    // 对话消息缓存前缀
    public static final String CONVERSATION_MESSAGE_KEY = "conversation:prompt:message:";
    // 对话消息缓存过期时间（秒）
    public static final int CONVERSATION_MESSAGE_TTL = 60 * 60;

    // 流式消息缓存前缀
    public static final String STREAMING_MESSAGE_KEY = "streaming:message:";
    // 流式消息缓存过期时间（秒）
    public static final int STREAMING_MESSAGE_TTL = 60 * 60;

    // 项目RAG检索结果缓存前缀
    public static final String PROJECT_RAG_KEY = "project:rag:";
    // 项目RAG检索结果缓存过期时间（秒）
    public static final int PROJECT_RAG_TTL = 5 * 60;

    // JWT Token缓存前缀
    public static final String JWT_TOKEN_KEY = "jwt:token:";
    // JWT Token过期时间（秒）- 7天
    public static final int JWT_TOKEN_TTL = 7 * 24 * 60 * 60;
}
