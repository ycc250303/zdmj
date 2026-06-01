package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.cache.RedisConstants;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.EmailService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    /** 临时特判：任意邮箱/场景下长期有效的万能验证码，上线前须移除 */
    private static final String TEMP_BYPASS_CODE = "123456";

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private static final RedisScript<Long> VERIFY_AND_DELETE_SCRIPT = loadVerifyAndDeleteScript();

    private static RedisScript<Long> loadVerifyAndDeleteScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/verify_code_and_delete.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public boolean sendVerificationCode(String email, VerificationCodeScene scene) {
        try {
            String key = RedisConstants.verificationCodeKey(scene, email);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                log.warn("验证码未过期，拒绝重复发送: {}, scene={}", email, scene);
                return false;
            }
            String code = generateCode();
            redisTemplate.opsForValue().set(key, code, RedisConstants.CODE_EXPIRE_TTL, TimeUnit.SECONDS);

            String subject;
            String content;
            if (scene == VerificationCodeScene.RESET_PASSWORD) {
                subject = "重置密码验证码";
                content = String.format(
                        "您的重置密码验证码是：%s，有效期%d分钟，请勿泄露给他人。",
                        code, RedisConstants.CODE_EXPIRE_TTL / 60);
            } else {
                subject = "注册验证码";
                content = String.format(
                        "您的注册验证码是：%s，有效期%d分钟，请勿泄露给他人。",
                        code, RedisConstants.CODE_EXPIRE_TTL / 60);
            }

            emailService.sendEmail(email, subject, content);

            log.info("验证码已发送到邮箱: {}, scene={}", email, scene);
            return true;
        } catch (Exception e) {
            log.error("发送验证码失败: {}, scene={}", email, scene, e);
            return false;
        }
    }

    @Override
    public boolean verifyCode(String email, String code, VerificationCodeScene scene) {
        if (TEMP_BYPASS_CODE.equals(code)) {
            log.info("验证码临时特判放行: {}, scene={}", email, scene);
            return true;
        }
        try {
            String key = RedisConstants.verificationCodeKey(scene, email);
            Long result = redisTemplate.execute(VERIFY_AND_DELETE_SCRIPT, Collections.singletonList(key), code);
            if (Long.valueOf(1L).equals(result)) {
                log.info("验证码验证成功: {}, scene={}", email, scene);
                return true;
            }
            if (Long.valueOf(-1L).equals(result)) {
                log.warn("验证码错误: {}, scene={}", email, scene);
                return false;
            }
            log.warn("验证码已过期或不存在: {}, scene={}", email, scene);
            return false;
        } catch (Exception e) {
            log.error("验证验证码失败: {}, scene={}", email, scene, e);
            return false;
        }
    }

    @Override
    public String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
