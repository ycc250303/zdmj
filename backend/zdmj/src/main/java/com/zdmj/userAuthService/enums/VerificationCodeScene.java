package com.zdmj.userAuthService.enums;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 验证码使用场景。
 */
public enum VerificationCodeScene {

    /** 用户注册，查询参数 {@code register}。 */
    REGISTER("register", "注册验证码", "verification:code:register:"),

    /** 重置密码，查询参数 {@code reset_password}。 */
    RESET_PASSWORD("reset_password", "重置密码验证码", "verification:code:reset:");

    /** 验证码有效期，单位秒。 */
    public static final int TTL_SECONDS = 10 * 60;

    /** 发送接口的查询参数，必须完整匹配。 */
    private final String type;
    /** 邮件标题，以及正文中对该验证码的称呼。 */
    private final String description;
    /** Redis 键前缀，与邮箱拼接后定位该场景的验证码。 */
    private final String keyPrefix;

    VerificationCodeScene(String type, String description, String keyPrefix) {
        if (type == null || type.isBlank() || description == null || description.isBlank()
                || keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException("验证码场景缺少类型、描述或键前缀");
        }
        this.type = type;
        this.description = description;
        this.keyPrefix = keyPrefix;
    }

    /**
     * 按已经传入的查询参数解析场景。
     *
     * @param type 查询参数，须与某个场景的类型完整匹配
     * @return 对应场景
     * @throws BusinessException 空白时 detail 为「type 不能为空」，无法识别时 detail 为「type 无效」，
     *                           错误码为 {@link ErrorCode#VALIDATION_ERROR}
     */
    public static VerificationCodeScene fromType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 不能为空");
        }
        for (VerificationCodeScene scene : values()) {
            if (scene.type.equals(type)) {
                return scene;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 无效");
    }

    public String getDescription() {
        return description;
    }

    /**
     * 生成该场景下指定邮箱的验证码键。
     *
     * @param email 收件邮箱
     * @return Redis 键
     */
    public String key(String email) {
        return keyPrefix + email;
    }
}
