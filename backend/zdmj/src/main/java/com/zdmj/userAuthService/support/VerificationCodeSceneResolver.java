package com.zdmj.userAuthService.support;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeScene;

/**
 * 解析发送验证码接口的 {@code type} 查询参数。
 */
public final class VerificationCodeSceneResolver {

    private VerificationCodeSceneResolver() {
    }

    /**
     * @param type 查询参数；{@code null} 表示未传，由调用方按邮箱是否已注册推断场景
     */
    public static VerificationCodeScene parseOptionalType(String type) {
        if (type == null) {
            return null;
        }
        if (type.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 不能为空");
        }
        return switch (type.trim().toLowerCase()) {
            case "register" -> VerificationCodeScene.REGISTER;
            case "reset_password", "reset-password", "reset" -> VerificationCodeScene.RESET_PASSWORD;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 无效");
        };
    }
}
