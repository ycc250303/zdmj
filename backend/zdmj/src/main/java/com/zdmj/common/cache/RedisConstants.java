package com.zdmj.common.cache;

import com.zdmj.userAuthService.enums.VerificationCodeScene;

public class RedisConstants {
    // 注册验证码前缀
    public static final String VERIFICATION_CODE_REGISTER_KEY = "verification:code:register:";
    // 重置密码验证码前缀
    public static final String VERIFICATION_CODE_RESET_KEY = "verification:code:reset:";

    public static String verificationCodeKey(VerificationCodeScene scene, String email) {
        return switch (scene) {
            case REGISTER -> VERIFICATION_CODE_REGISTER_KEY + email;
            case RESET_PASSWORD -> VERIFICATION_CODE_RESET_KEY + email;
        };
    }
    // 验证码过期时间（分钟）
    public static final int CODE_EXPIRE_TTL = 10 * 60;

    // 空值标记前缀（用于防止缓存穿透）
    public static final String NULL_VALUE_KEY = "null:value:";

    // JWT Token缓存前缀
    public static final String JWT_TOKEN_KEY = "jwt:token:";
    // JWT Token过期时间（秒）- 7天
    public static final int JWT_TOKEN_TTL = 7 * 24 * 60 * 60;

    // 岗位详情缓存前缀
    public static final String JOB_DETAIL_KEY = "job:detail:";
    // 岗位详情缓存过期时间（秒）- 10 分钟
    public static final int JOB_DETAIL_TTL = 10 * 60;
    // 岗位不存在时的空值标记 TTL（秒）
    public static final int JOB_DETAIL_NULL_TTL = 60;

}
