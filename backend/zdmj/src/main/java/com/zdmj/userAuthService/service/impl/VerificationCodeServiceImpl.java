package com.zdmj.userAuthService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeKeys;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.mapper.UserMapper;
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

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private static final RedisScript<Long> VERIFY_AND_DELETE_SCRIPT = loadVerifyAndDeleteScript();

    private static RedisScript<Long> loadVerifyAndDeleteScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/verify_code_and_delete.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private VerificationCodeScene resolveScene(String email, String type) {
        if (type == null) {
            boolean registered = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
            return registered ? VerificationCodeScene.RESET_PASSWORD : VerificationCodeScene.REGISTER;
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

    /**
     * 解析显式类型或根据邮箱注册状态确定场景，检查重复发送后保存验证码并发送对应邮件。
     *
     * @param email 邮箱地址
     * @param type 验证码类型；未传时根据邮箱注册状态确定场景
     * @throws BusinessException 类型非法时返回参数校验错误；验证码尚未过期或发送失败时返回发送失败错误
     */
    @Override
    public void sendVerificationCode(String email, String type) {
        VerificationCodeScene scene = resolveScene(email, type);
        try {
            String key = VerificationCodeKeys.key(scene, email);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                log.warn("验证码未过期，拒绝重复发送: {}, scene={}", email, scene);
                throw new BusinessException(ErrorCode.CAPTCHA_SEND_FAILED);
            }
            String code = generateCode();
            redisTemplate.opsForValue().set(key, code, VerificationCodeKeys.TTL_SECONDS, TimeUnit.SECONDS);

            String subject;
            String content;
            if (scene == VerificationCodeScene.RESET_PASSWORD) {
                subject = "重置密码验证码";
                content = String.format(
                        "您的重置密码验证码是：%s，有效期%d分钟，请勿泄露给他人。",
                        code, VerificationCodeKeys.TTL_SECONDS / 60);
            } else {
                subject = "注册验证码";
                content = String.format(
                        "您的注册验证码是：%s，有效期%d分钟，请勿泄露给他人。",
                        code, VerificationCodeKeys.TTL_SECONDS / 60);
            }

            emailService.sendEmail(email, subject, content);

            log.info("验证码已发送到邮箱: {}, scene={}", email, scene);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送验证码失败: {}, scene={}", email, scene, e);
            throw new BusinessException(ErrorCode.CAPTCHA_SEND_FAILED, e);
        }
    }

    @Override
    public boolean verifyCode(String email, String code, VerificationCodeScene scene) {
        try {
            String key = VerificationCodeKeys.key(scene, email);
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

    private String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
