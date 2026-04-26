package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.cache.RedisConstants;
import com.zdmj.userAuthService.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EmailService emailService;

    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new VerificationCodeServiceImpl(redisTemplate, emailService);
    }

    @Test
    void sendVerificationCode_success_shouldStoreCodeAndSendEmail() {
        boolean sent = service.sendVerificationCode("a@zdmj.com");

        assertTrue(sent);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), codeCaptor.capture(), eq((long) RedisConstants.CODE_EXPIRE_TTL), eq(TimeUnit.SECONDS));
        verify(emailService).sendEmail(eq("a@zdmj.com"), eq("注册验证码"), anyString());
        assertEquals(RedisConstants.VERIFICATION_CODE_KEY + "a@zdmj.com", keyCaptor.getValue());
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
    }

    @Test
    void sendVerificationCode_whenRedisThrows_shouldReturnFalse() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        boolean sent = service.sendVerificationCode("b@zdmj.com");

        assertFalse(sent);
    }

    @Test
    void verifyCode_whenStoredCodeMissing_shouldReturnFalse() {
        when(valueOperations.get(RedisConstants.VERIFICATION_CODE_KEY + "c@zdmj.com")).thenReturn(null);

        boolean valid = service.verifyCode("c@zdmj.com", "123456");

        assertFalse(valid);
    }

    @Test
    void verifyCode_whenCodeMatches_shouldDeleteAndReturnTrue() {
        String key = RedisConstants.VERIFICATION_CODE_KEY + "d@zdmj.com";
        when(valueOperations.get(key)).thenReturn("123456");

        boolean valid = service.verifyCode("d@zdmj.com", "123456");

        assertTrue(valid);
        verify(redisTemplate).delete(key);
    }

    @Test
    void verifyCode_whenCodeNotMatches_shouldReturnFalseWithoutDelete() {
        String key = RedisConstants.VERIFICATION_CODE_KEY + "e@zdmj.com";
        when(valueOperations.get(key)).thenReturn("123456");

        boolean valid = service.verifyCode("e@zdmj.com", "000000");

        assertFalse(valid);
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void verifyCode_whenOpsThrows_shouldReturnFalse() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("boom"));

        boolean valid = service.verifyCode("f@zdmj.com", "123456");

        assertFalse(valid);
    }

    @Test
    void generateCode_shouldReturnSixDigitNumberString() {
        String code = service.generateCode();
        assertTrue(code.matches("\\d{6}"));
    }
}
