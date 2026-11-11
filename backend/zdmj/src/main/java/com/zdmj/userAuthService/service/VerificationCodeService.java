package com.zdmj.userAuthService.service;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeScene;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

    /**
     * 按查询参数指定的场景检查发送限制，并将验证码发送到邮箱。
     * 可接受的类型与邮件描述定义在 {@link VerificationCodeScene}，查询参数须与场景类型完整匹配。
     *
     * @param email 邮箱地址
     * @param type 验证码类型，调用方必须传入
     * @throws BusinessException {@code type} 为空白时 detail 为「type 不能为空」，无法识别时 detail 为「type 无效」，
     *                           错误码为 {@link ErrorCode#VALIDATION_ERROR}；
     *                           验证码尚未过期时返回 {@link ErrorCode#CAPTCHA_SEND_FAILED}；
     *                           Redis 或邮件故障分别返回 {@link ErrorCode#CAPTCHA_STORE_UNAVAILABLE}、
     *                           {@link ErrorCode#CAPTCHA_MAIL_UNAVAILABLE}，保留故障原因
     */
    void sendVerificationCode(String email, String type);

    /**
     * 通过 Lua 原子校验并消费验证码；校验成功后立即失效，后续数据库失败时需要重新获取。
     *
     * @param email 邮箱地址
     * @param code 验证码
     * @param scene 验证码场景，须与发送时一致
     * @throws BusinessException 验证码过期、输入错误或 Redis 不可用
     * @throws IllegalStateException 脚本返回非法结果
     */
    void verifyAndConsume(String email, String code, VerificationCodeScene scene);
}
