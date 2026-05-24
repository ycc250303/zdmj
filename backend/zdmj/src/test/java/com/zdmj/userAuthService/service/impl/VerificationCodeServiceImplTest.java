package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.cache.RedisConstants;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailService emailService;

    private VerificationCodeServiceImpl verificationCodeService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        verificationCodeService = new VerificationCodeServiceImpl(redisTemplate, emailService);
    }

    @Test
    void sendVerificationCode_whenRedisThrows_shouldReturnFalse() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.REGISTER, email);
        doThrow(new RuntimeException("redis down")).when(valueOperations)
                .set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        boolean result = verificationCodeService.sendVerificationCode(email, VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(valueOperations).set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verifyNoInteractions(emailService);
    }

    @Test
    void sendVerificationCode_whenRegister_shouldSaveCodeAndSendRegisterEmail() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.REGISTER, email);

        boolean result = verificationCodeService.sendVerificationCode(email, VerificationCodeScene.REGISTER);

        assertEquals(true, result);
        verify(valueOperations).set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verify(emailService).sendEmail(eq(email), eq("注册验证码"), anyString());
    }

    @Test
    void sendVerificationCode_whenResetPassword_shouldSaveCodeAndSendResetEmail() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.RESET_PASSWORD, email);

        boolean result = verificationCodeService.sendVerificationCode(email, VerificationCodeScene.RESET_PASSWORD);

        assertEquals(true, result);
        verify(valueOperations).set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verify(emailService).sendEmail(eq(email), eq("重置密码验证码"), anyString());
    }

    @Test
    void verifyCode_whenCodeMissing_shouldReturnFalseAndNotDelete() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456")))
                .thenReturn(0L);

        boolean result = verificationCodeService.verifyCode(email, "123456", VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456"));
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void verifyCode_whenCodeMismatch_shouldReturnFalseAndNotDelete() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456")))
                .thenReturn(-1L);

        boolean result = verificationCodeService.verifyCode(email, "123456", VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456"));
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void verifyCode_whenRedisThrows_shouldReturnFalse() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456")))
                .thenThrow(new RuntimeException("redis timeout"));

        boolean result = verificationCodeService.verifyCode(email, "123456", VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456"));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verifyCode_whenCodeMatches_shouldReturnTrueAndDeleteKey() {
        String email = "test@demo.com";
        String key = RedisConstants.verificationCodeKey(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456")))
                .thenReturn(1L);

        boolean result = verificationCodeService.verifyCode(email, "123456", VerificationCodeScene.REGISTER);

        assertEquals(true, result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq("123456"));
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void generateCode_shouldBeSixDigitNumber() {
        String code = verificationCodeService.generateCode();

        assertEquals(6, code.length());
        assertEquals(code, String.format("%06d", Integer.parseInt(code)));
    }
}
