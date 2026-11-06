package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeKeys;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.mapper.UserMapper;
import com.zdmj.userAuthService.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private UserMapper userMapper;

    private VerificationCodeServiceImpl verificationCodeService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        verificationCodeService = new VerificationCodeServiceImpl(redisTemplate, emailService, userMapper);
    }

    @Test
    void sendVerificationCode_whenRedisThrows_shouldThrowSendFailed() {
        String email = "test@demo.com";
        String key = VerificationCodeKeys.key(VerificationCodeScene.REGISTER, email);
        RuntimeException failure = new RuntimeException("redis down");
        doThrow(failure).when(valueOperations)
                .set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verificationCodeService.sendVerificationCode(email, "register"));

        assertEquals(ErrorCode.CAPTCHA_SEND_FAILED.getCode(), ex.getCode());
        assertSame(failure, ex.getCause());
        verify(valueOperations).set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verifyNoInteractions(emailService);
    }

    @Test
    void sendVerificationCode_whenCodeStillValid_shouldThrowSendFailedWithoutResend() {
        String email = "test@demo.com";
        String key = VerificationCodeKeys.key(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.hasKey(key)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verificationCodeService.sendVerificationCode(email, "register"));

        assertEquals(ErrorCode.CAPTCHA_SEND_FAILED.getCode(), ex.getCode());
        verify(redisTemplate).hasKey(key);
        verify(valueOperations, never()).set(eq(key), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verifyNoInteractions(emailService);
    }

    @ParameterizedTest
    @CsvSource({"register, REGISTER, 注册验证码", "reset_password, RESET_PASSWORD, 重置密码验证码",
            "reset-password, RESET_PASSWORD, 重置密码验证码", "reset, RESET_PASSWORD, 重置密码验证码",
            "' REGISTER ', REGISTER, 注册验证码", "' RESET ', RESET_PASSWORD, 重置密码验证码"})
    void sendVerificationCode_whenExplicitType_shouldSendMatchingCodeWithoutUserLookup(
            String type, VerificationCodeScene scene, String subject) {
        verificationCodeService.sendVerificationCode("test@demo.com", type);

        assertCodeSavedAndMailed(scene, subject);
        verifyNoInteractions(userMapper);
    }

    @ParameterizedTest
    @CsvSource({"0, REGISTER, 注册验证码", "1, RESET_PASSWORD, 重置密码验证码"})
    void sendVerificationCode_whenTypeOmitted_shouldChooseSceneByRegistration(
            long userCount, VerificationCodeScene scene, String subject) {
        when(userMapper.selectCount(any())).thenReturn(userCount);

        verificationCodeService.sendVerificationCode("test@demo.com", null);

        assertCodeSavedAndMailed(scene, subject);
        verify(userMapper).selectCount(any());
    }

    @Test
    void sendVerificationCode_whenMailThrows_shouldThrowSendFailed() {
        RuntimeException failure = new RuntimeException("mail unavailable");
        doThrow(failure).when(emailService).sendEmail(anyString(), anyString(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verificationCodeService.sendVerificationCode("test@demo.com", "register"));

        assertEquals(ErrorCode.CAPTCHA_SEND_FAILED.getCode(), ex.getCode());
        assertSame(failure, ex.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "invalid"})
    void sendVerificationCode_whenInvalidType_shouldRejectBeforeExternalAccess(String type) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> verificationCodeService.sendVerificationCode("test@demo.com", type));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals(type.isBlank() ? "type 不能为空" : "type 无效", ex.getMessage());
        verifyNoInteractions(userMapper, redisTemplate, emailService);
    }

    private void assertCodeSavedAndMailed(VerificationCodeScene scene, String subject) {
        String email = "test@demo.com";
        String key = VerificationCodeKeys.key(scene, email);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);

        verify(valueOperations).set(eq(key), code.capture(), eq((long) VerificationCodeKeys.TTL_SECONDS), eq(TimeUnit.SECONDS));
        assertTrue(code.getValue().matches("[1-9][0-9]{5}"));
        verify(emailService).sendEmail(eq(email), eq(subject), content.capture());
        assertEquals("您的" + subject + "是：" + code.getValue() + "，有效期10分钟，请勿泄露给他人。", content.getValue());
    }

    @Test
    void verifyCode_whenCodeMissing_shouldReturnFalseAndNotDelete() {
        String email = "test@demo.com";
        String code = "987654";
        String key = VerificationCodeKeys.key(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code)))
                .thenReturn(0L);

        boolean result = verificationCodeService.verifyCode(email, code, VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code));
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void verifyCode_whenCodeMismatch_shouldReturnFalseAndNotDelete() {
        String email = "test@demo.com";
        String code = "987654";
        String key = VerificationCodeKeys.key(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code)))
                .thenReturn(-1L);

        boolean result = verificationCodeService.verifyCode(email, code, VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code));
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void verifyCode_whenRedisThrows_shouldReturnFalse() {
        String email = "test@demo.com";
        String code = "987654";
        String key = VerificationCodeKeys.key(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code)))
                .thenThrow(new RuntimeException("redis timeout"));

        boolean result = verificationCodeService.verifyCode(email, code, VerificationCodeScene.REGISTER);

        assertFalse(result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verifyCode_whenCodeMatches_shouldReturnTrueAndDeleteKey() {
        String email = "test@demo.com";
        String code = "987654";
        String key = VerificationCodeKeys.key(VerificationCodeScene.REGISTER, email);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code)))
                .thenReturn(1L);

        boolean result = verificationCodeService.verifyCode(email, code, VerificationCodeScene.REGISTER);

        assertEquals(true, result);
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(key)), eq(code));
        verify(redisTemplate, never()).delete(key);
    }
}
