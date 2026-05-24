package com.zdmj.userAuthService.service;

import com.zdmj.userAuthService.enums.VerificationCodeScene;

/**
 * 验证码服务接口
 */
public interface VerificationCodeService {

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
