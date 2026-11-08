package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeError;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.EmailService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 管理验证码的发送与一次性消费，Redis 记录保存发送标识和六位验证码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RedisScript<Long> DELETE_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/verify_code_and_delete.lua"));
        script.setResultType(Long.class);
        DELETE_SCRIPT = script;
    }

    /**
     * 原子建立带有效期的验证码记录并发送邮件，邮件失败时核对完整记录后清理。
     *
     * @param email 邮箱地址
     * @param type 验证码场景，须与场景类型完整匹配
     * @throws BusinessException 类型无效、重复发送、Redis 故障或邮件发送失败
     */
    @Override
    public void sendVerificationCode(String email, String type) {
        // 1. 解析场景
        VerificationCodeScene scene = VerificationCodeScene.fromType(type);
        // 2. 生成验证码
        String key = scene.key(email);
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        String record = UUID.randomUUID() + ":" + code;
        Boolean created;
        // 3. 写入Redis
        try {
            created = redisTemplate.opsForValue().setIfAbsent(key, record,
                    Duration.ofSeconds(VerificationCodeScene.TTL_SECONDS));
        } catch (DataAccessException failure) {
            throw new BusinessException(ErrorCode.CAPTCHA_STORE_UNAVAILABLE, failure);
        }
        if (created == null) {
            throw new IllegalStateException("验证码写入未返回结果");
        }
        if (!created) {
            throw new BusinessException(ErrorCode.CAPTCHA_SEND_FAILED, "验证码尚未过期，请勿重复发送");
        }
        // 4. 发送邮件
        String subject = scene.getDescription();
        String content = String.format("您的%s是：%s，有效期%d分钟，请勿泄露给他人。",
                subject, code, VerificationCodeScene.TTL_SECONDS / 60);
        try {
            emailService.sendEmail(email, subject, content);
        } catch (RuntimeException failure) {
            try {
                long deleted = delete(key, "cancel", record);
                if (deleted != 0 && deleted != 1) {
                    throw new IllegalStateException("验证码清理脚本返回未知结果: " + deleted);
                }
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                log.error("邮件失败后的验证码清理失败", cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * 删除验证码并验证结果
     *
     * @param email 邮箱地址
     * @param code 验证码
     * @param scene 验证码场景
     * @throws BusinessException 验证码不存在、已使用、不匹配或 Redis 故障
     */
    @Override
    public void verifyAndConsume(String email, String code, VerificationCodeScene scene) {
        long result = delete(scene.key(email), "verify", code);
        if (result != 1) {
            throw VerificationCodeError.rejection(result);
        }
    }

    /**
     * 执行 Redis 脚本删除验证码。
     *
     * @param key Redis 键
     * @param action 操作类型
     * @param expected 期望值
     * @return Redis 结果
     * @throws BusinessException Redis 故障
     */
    private long delete(String key, String action, String expected) {
        Long result;
        try {
            result = redisTemplate.execute(DELETE_SCRIPT, List.of(key), action, expected);
        } catch (DataAccessException failure) {
            throw new BusinessException(ErrorCode.CAPTCHA_STORE_UNAVAILABLE, failure);
        }
        if (result == null) {
            throw new IllegalStateException("验证码脚本未返回结果");
        }
        return result;
    }
}
