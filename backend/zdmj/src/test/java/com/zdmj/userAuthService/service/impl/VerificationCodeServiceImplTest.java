package com.zdmj.userAuthService.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailSendException;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.EmailService;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceImplTest {

    private static final String EMAIL = "test@demo.com";
    private static final String CODE = "123456";
    private static final String KEY = VerificationCodeScene.REGISTER.key(EMAIL);
    private static final Duration TTL = Duration.ofSeconds(VerificationCodeScene.TTL_SECONDS);

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private EmailService mail;
    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VerificationCodeServiceImpl(redis, mail);
    }

    @ParameterizedTest
    @CsvSource({"register, REGISTER, 注册验证码", "reset_password, RESET_PASSWORD, 重置密码验证码"})
    void 指定类型_原子建立带有效期的记录并发送对应邮件(String type, VerificationCodeScene scene, String subject) {
        creationReturns(true);
        service.sendVerificationCode(EMAIL, type);
        var record = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(scene.key(EMAIL)), record.capture(), eq(TTL));
        assertThat(record.getValue()).matches("[0-9a-f-]{36}:[1-9][0-9]{5}");
        String code = record.getValue().substring(record.getValue().lastIndexOf(':') + 1);
        verify(mail).sendEmail(EMAIL, subject, "您的" + subject + "是：" + code + "，有效期10分钟，请勿泄露给他人。");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "reset", "reset-password", "RESET_PASSWORD", "Register"})
    void 空白或无法识别的类型_拒绝请求且不访问外部服务(String type) {
        BusinessException failure = assertError(() -> service.sendVerificationCode(EMAIL, type), ErrorCode.VALIDATION_ERROR);
        assertThat(failure).hasMessage(type.isBlank() ? "type 不能为空" : "type 无效");
        verifyNoInteractions(redis, mail);
    }

    @Test
    void 类型缺失_拒绝请求且不访问外部服务() {
        assertThat(assertError(() -> service.sendVerificationCode(EMAIL, null), ErrorCode.VALIDATION_ERROR))
                .hasMessage("type 不能为空");
        verifyNoInteractions(redis, mail);
    }

    @Test
    void 已有验证码_拒绝重复发送且不发送邮件() {
        creationReturns(false);
        assertError(() -> service.sendVerificationCode(EMAIL, "register"), ErrorCode.CAPTCHA_SEND_FAILED);
        verifyNoInteractions(mail);
    }

    @Test
    void 发送时Redis故障_传播存储异常并保留原因() {
        var cause = new RedisConnectionFailureException("redis down");
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenThrow(cause);
        assertThat(assertError(() -> service.sendVerificationCode(EMAIL, "register"), ErrorCode.CAPTCHA_STORE_UNAVAILABLE))
                .hasCause(cause);
        verifyNoInteractions(mail);
    }

    @Test
    void 邮件故障_核对完整记录后清理并保留故障原因() {
        creationReturns(true);
        scriptsReturn(1L);
        var cause = new MailSendException("smtp down");
        doThrow(new BusinessException(ErrorCode.CAPTCHA_MAIL_UNAVAILABLE, cause))
                .when(mail).sendEmail(anyString(), anyString(), anyString());
        assertThat(assertError(() -> service.sendVerificationCode(EMAIL, "register"), ErrorCode.CAPTCHA_MAIL_UNAVAILABLE))
                .hasCause(cause);
        var record = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(KEY), record.capture(), eq(TTL));
        verify(redis).execute(anyScript(), eq(List.of(KEY)), eq("cancel"), eq(record.getValue()));
    }

    @Test
    void 邮件及清理同时失败_保留邮件根因并附加Redis故障() {
        creationReturns(true);
        var mailFailure = new MailSendException("smtp down");
        var redisFailure = new RedisConnectionFailureException("redis down");
        when(redis.execute(anyScript(), anyList(), any(Object[].class))).thenThrow(redisFailure);
        doThrow(new BusinessException(ErrorCode.CAPTCHA_MAIL_UNAVAILABLE, mailFailure))
                .when(mail).sendEmail(anyString(), anyString(), anyString());
        BusinessException failure = assertError(() -> service.sendVerificationCode(EMAIL, "register"),
                ErrorCode.CAPTCHA_MAIL_UNAVAILABLE);
        assertThat(failure).hasCause(mailFailure);
        assertThat(failure.getSuppressed()).hasSize(1);
        assertThat(failure.getSuppressed()[0]).hasCause(redisFailure);
    }

    @Test
    void 邮件调用出现编码缺陷_清理本次记录并传播原始缺陷() {
        creationReturns(true);
        scriptsReturn(1L);
        var cause = new NullPointerException("mail implementation bug");
        doThrow(cause).when(mail).sendEmail(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL, "register")).isSameAs(cause);
        verify(redis).execute(anyScript(), eq(List.of(KEY)), eq("cancel"), anyString());
    }

    @ParameterizedTest
    @CsvSource({"0, 验证码已过期或不存在", "-1, 验证码不正确"})
    void 校验被拒绝_返回明确原因(long result, String detail) {
        scriptsReturn(result);
        assertThat(assertError(() -> service.verifyAndConsume(EMAIL, CODE, VerificationCodeScene.REGISTER),
                ErrorCode.CAPTCHA_ERROR)).hasMessage(detail);
    }

    @Test
    void 校验时Redis故障_传播存储故障并保留原因() {
        var cause = new RedisConnectionFailureException("redis down");
        when(redis.execute(anyScript(), anyList(), any(Object[].class))).thenThrow(cause);
        assertThat(assertError(() -> service.verifyAndConsume(EMAIL, CODE, VerificationCodeScene.REGISTER),
                ErrorCode.CAPTCHA_STORE_UNAVAILABLE)).hasCause(cause);
    }

    @Test
    void 校验成功_原子消费对应场景的验证码() {
        scriptsReturn(1L);
        service.verifyAndConsume(EMAIL, CODE, VerificationCodeScene.RESET_PASSWORD);
        verify(redis).execute(anyScript(), eq(List.of(VerificationCodeScene.RESET_PASSWORD.key(EMAIL))),
                eq("verify"), eq(CODE));
    }

    @ParameterizedTest
    @ValueSource(longs = {2, -2})
    void 未知校验脚本结果_作为实现错误暴露(long result) {
        scriptsReturn(result);
        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, CODE, VerificationCodeScene.REGISTER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 写入返回空值_作为实现错误暴露且不发送邮件() {
        creationReturns(null);
        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL, "register"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mail);
    }

    @Test
    void 校验脚本返回空值_作为实现错误暴露() {
        scriptsReturn(null);
        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, CODE, VerificationCodeScene.REGISTER))
                .isInstanceOf(IllegalStateException.class);
    }

    private void creationReturns(Boolean created) {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(created);
    }

    private void scriptsReturn(Long result) {
        when(redis.execute(anyScript(), anyList(), any(Object[].class))).thenReturn(result);
    }

    private static RedisScript<Long> anyScript() {
        return org.mockito.ArgumentMatchers.<RedisScript<Long>>any();
    }

    private static BusinessException assertError(Runnable action, ErrorCode error) {
        BusinessException failure = assertThrows(BusinessException.class, action::run);
        assertThat(failure.getErrorCode()).isEqualTo(error);
        return failure;
    }
}
