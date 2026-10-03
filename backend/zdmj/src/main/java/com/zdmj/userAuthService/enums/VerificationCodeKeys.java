package com.zdmj.userAuthService.enums;

/**
 * 验证码在 Redis 中的键和有效期。
 */
public final class VerificationCodeKeys {

    /**
     * 验证码有效期，单位秒。
     */
    public static final int TTL_SECONDS = 10 * 60;

    private static final String REGISTER_PREFIX = "verification:code:register:";
    private static final String RESET_PREFIX = "verification:code:reset:";

    private VerificationCodeKeys() {
    }

    /**
     * 按场景和邮箱生成验证码键。
     *
     * @param scene 注册或重置密码
     * @param email 收件邮箱
     * @return Redis 键
     */
    public static String key(VerificationCodeScene scene, String email) {
        return switch (scene) {
            case REGISTER -> REGISTER_PREFIX + email;
            case RESET_PASSWORD -> RESET_PREFIX + email;
        };
    }
}
