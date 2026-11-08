package com.zdmj.userAuthService.enums;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 验证码的可预期拒绝情况；码值对应验证码 Lua 脚本的返回值，基础设施故障通过异常传播。
 */
public enum VerificationCodeError {

    /** Redis 记录不存在或已到期，对应 Lua 返回 0。 */
    EXPIRED(0, "验证码已过期或不存在"),
    /** 输入与保存的验证码不符，对应 Lua 返回 -1。 */
    MISMATCH(-1, "验证码不正确");

    /** 验证码校验脚本的拒绝码，仅使用 0、-1。 */
    private final long scriptCode;
    /** 返回给用户的拒绝原因，对外统一使用验证码错误码 2003。 */
    private final String detail;

    VerificationCodeError(long scriptCode, String detail) {
        this.scriptCode = scriptCode;
        this.detail = detail;
    }

    /**
     * 将 Lua 的拒绝结果转换为保留既有验证码错误码的业务异常。
     *
     * @param result Lua 返回值；1 表示成功，由调用方处理
     * @return 对应拒绝原因的业务异常
     * @throws IllegalStateException Lua 返回未知值，说明脚本或调用存在实现错误
     */
    public static BusinessException rejection(long result) {
        for (VerificationCodeError error : values()) {
            if (error.scriptCode == result) {
                return new BusinessException(ErrorCode.CAPTCHA_ERROR, error.detail);
            }
        }
        throw new IllegalStateException("验证码脚本返回未知结果: " + result);
    }
}
