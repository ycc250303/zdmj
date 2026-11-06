package com.zdmj.userAuthService.service;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeScene;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 解析发送验证码接口的 {@code type} 查询参数。
     *
     * @param type 查询参数；{@code null} 表示未传，由调用方按邮箱是否已注册推断场景
     * @return 显式场景；未传时返回 {@code null}
     * @throws BusinessException {@code type} 为空白或无法识别时，错误码为 {@link ErrorCode#VALIDATION_ERROR}
     */
    VerificationCodeScene parseOptionalType(String type);

    /**
     * 发送验证码到邮箱
     *
     * @param email 邮箱地址
     * @param scene 验证码场景（注册 / 重置密码）
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String email, VerificationCodeScene scene);

    /**
     * 验证验证码
     *
     * @param email 邮箱地址
     * @param code  验证码
     * @param scene 验证码场景（须与发送时一致）
     * @return 是否验证通过
     */
    boolean verifyCode(String email, String code, VerificationCodeScene scene);

    /**
     * 生成6位数字验证码
     * 
     * @return 验证码
     */
    String generateCode();
}
