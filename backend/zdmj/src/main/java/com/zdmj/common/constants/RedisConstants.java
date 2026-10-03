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

    // ========== Redis Stream 异步任务 ==========
    public static final String LLM_STREAM_KEY = "zdmj:llm:stream";
    public static final String EMBED_STREAM_KEY = "zdmj:embed:stream";
    public static final String LLM_STREAM_GROUP = "zdmj:llm:group";
    public static final String EMBED_STREAM_GROUP = "zdmj:embed:group";
    /** Stream 近似裁剪上限，防止撑爆 */
    public static final long STREAM_MAXLEN = 1000;
    public static final int STREAM_BLOCK_SECONDS = 2;
    public static final int STREAM_READ_COUNT = 1;
    /** 读取本消费者未 ACK 消息的起始 ID（启动排空 PEL，不用 XCLAIM） */
    public static final String STREAM_OWN_PENDING_OFFSET = "0-0";

    public static final String STREAM_FIELD_TASK_ID = "taskId";

}
