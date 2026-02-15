package com.zdmj.userAuthService.service.impl;

import com.zdmj.userAuthService.service.EmailService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EmailService emailService;

    /**
     * Redis key 前缀
     */
    private static final String VERIFICATION_CODE_PREFIX = "verification:code:";

    /**
     * 验证码过期时间（分钟）
     */
    private static final int CODE_EXPIRE_MINUTES = 5;

    @Override
    public boolean sendVerificationCode(String email) {
        try {
            // 生成验证码
            String code = generateCode();

            // 存储到Redis，设置过期时间
            String key = VERIFICATION_CODE_PREFIX + email;
            redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

            // 发送邮件
            String subject = "注册验证码";
            String content = String.format(
                    "您的注册验证码是：%s，有效期%d分钟，请勿泄露给他人。",
                    code, CODE_EXPIRE_MINUTES);

            emailService.sendEmail(email, subject, content);

            log.info("验证码已发送到邮箱: {}", email);
            return true;
        } catch (Exception e) {
            log.error("发送验证码失败: {}", email, e);
            return false;
        }
    }

    @Override
    public boolean verifyCode(String email, String code) {
        try {
            String key = VERIFICATION_CODE_PREFIX + email;
            String storedCode = redisTemplate.opsForValue().get(key);

            if (storedCode == null) {
                log.warn("验证码已过期或不存在: {}", email);
                return false;
            }

            boolean isValid = storedCode.equals(code);
            if (isValid) {
                // 验证成功后删除验证码
                redisTemplate.delete(key);
                log.info("验证码验证成功: {}", email);
            } else {
                log.warn("验证码错误: {}", email);
            }

            return isValid;
        } catch (Exception e) {
            log.error("验证验证码失败: {}", email, e);
            return false;
        }
    }

    @Override
    public String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 生成100000-999999之间的6位数字
        return String.valueOf(code);
    }
}
