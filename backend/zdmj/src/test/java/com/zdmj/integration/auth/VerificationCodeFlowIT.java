package com.zdmj.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.EmailService;
import com.zdmj.userAuthService.service.UserLoginService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import com.zdmj.userAuthService.service.impl.VerificationCodeServiceImpl;

@AutoConfigureMockMvc
class VerificationCodeFlowIT extends IntegrationTestBase {

    private static final String EMAIL = "verification@example.com";
    private static final String KEY = VerificationCodeScene.REGISTER.key(EMAIL);
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private final TestUsers testUsers;
    private final UserLoginService users;
    private final VerificationCodeService verification;

    @Autowired
    VerificationCodeFlowIT(DatabaseCleaner cleaner, StringRedisTemplate redis, JdbcTemplate jdbc,
            MockMvc mvc, TestUsers testUsers, UserLoginService users, VerificationCodeService verification) {
        super(cleaner);
        this.redis = redis;
        this.jdbc = jdbc;
        this.mvc = mvc;
        this.testUsers = testUsers;
        this.users = users;
        this.verification = verification;
    }

    @Test
    void 邮件发送失败_删除本次验证码_允许立即重新发送() {
        var service = service((email, subject, content) -> {
            throw new BusinessException(ErrorCode.CAPTCHA_MAIL_UNAVAILABLE, new MailSendException("smtp down"));
        });
        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL, "register")).hasRootCauseMessage("smtp down");
        assertThat(redis.hasKey(KEY)).isFalse();
        verification.sendVerificationCode(EMAIL, "register");
        assertThat(code(KEY)).matches("[1-9][0-9]{5}");
    }

    @Test
    void 并发发送_首个请求尚未完成邮件发送_第二个请求被拒绝且记录保持不变() throws Exception {
        CountDownLatch mailing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        var service = service((email, subject, content) -> {
            mailing.countDown();
            await(finish);
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> service.sendVerificationCode(EMAIL, "register"));
            try {
                assertThat(mailing.await(5, TimeUnit.SECONDS)).isTrue();
                String record = redis.opsForValue().get(KEY);
                assertThatThrownBy(() -> service.sendVerificationCode(EMAIL, "register"))
                        .isInstanceOf(BusinessException.class).hasMessage("验证码尚未过期，请勿重复发送");
                assertThat(redis.opsForValue().get(KEY)).isEqualTo(record);
            } finally {
                finish.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void 同时发送_只有一个请求建立验证码并发送邮件() throws Exception {
        AtomicInteger sent = new AtomicInteger();
        var service = service((email, subject, content) -> sent.incrementAndGet());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                requests.add(pool.submit(() -> {
                    await(start);
                    try {
                        service.sendVerificationCode(EMAIL, "register");
                        return true;
                    } catch (BusinessException failure) {
                        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_SEND_FAILED);
                        return false;
                    }
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> request : requests) {
                if (request.get(5, TimeUnit.SECONDS)) { succeeded++; }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(sent).hasValue(1);
        }
    }

    @Test
    void 邮件送达后立即注册_用户创建成功且验证码已消费() {
        var service = service((email, subject, content) -> {
            String code = code(KEY);
            assertThat(content).contains(code);
            users.register(registration("mail-user", code));
        });
        service.sendVerificationCode(EMAIL, "register");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, EMAIL)).isEqualTo(1);
        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void 旧邮件发送失败_新记录验证码数字相同_不能删除新记录() {
        AtomicReference<String> replacement = new AtomicReference<>();
        var service = service((email, subject, content) -> {
            String oldCode = code(KEY);
            verification.verifyAndConsume(email, oldCode, VerificationCodeScene.REGISTER);
            verification.sendVerificationCode(EMAIL, "register");
            String newRecord = redis.opsForValue().get(KEY);
            String sameCodeRecord = newRecord.substring(0, newRecord.lastIndexOf(':') + 1) + oldCode;
            redis.opsForValue().set(KEY, sameCodeRecord, Duration.ofSeconds(120));
            replacement.set(sameCodeRecord);
            throw new BusinessException(ErrorCode.CAPTCHA_MAIL_UNAVAILABLE, new MailSendException("old mail failed"));
        });
        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL, "register")).hasRootCauseMessage("old mail failed");
        assertThat(redis.opsForValue().get(KEY)).isEqualTo(replacement.get());
        assertThat(redis.getExpire(KEY, TimeUnit.SECONDS)).isBetween(1L, 120L);
    }

    @Test
    void 注册数据库写入失败_验证码已消费_重新获取后可注册() {
        verification.sendVerificationCode(EMAIL, "register");
        var request = registration("x".repeat(51), code(KEY));
        assertThatThrownBy(() -> users.register(request)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, EMAIL)).isZero();
        assertThat(redis.hasKey(KEY)).isFalse();

        request.setUsername("verification-user");
        assertThatThrownBy(() -> users.register(request)).isInstanceOf(BusinessException.class)
                .hasMessage("验证码已过期或不存在");
        verification.sendVerificationCode(EMAIL, "register");
        request.setVerificationCode(code(KEY));
        users.register(request);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, EMAIL)).isEqualTo(1);
        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void 重置密码数据库写入失败_原密码不变且验证码已消费_重新获取后可重置() throws Exception {
        var user = testUsers.createPair().userA();
        String key = VerificationCodeScene.RESET_PASSWORD.key(user.getEmail());
        verification.sendVerificationCode(user.getEmail(), "reset_password");
        String body = resetBody(user.getEmail(), code(key));
        String oldPassword = jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, user.getId());
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT test_block_reset CHECK (id <> " + user.getId()
                + " OR password = '" + oldPassword + "')");
        try {
            mvc.perform(put("/api/zdmj/users/password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(1008));
            assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, user.getId()))
                    .isEqualTo(oldPassword);
            assertThat(redis.hasKey(key)).isFalse();
        } finally {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT test_block_reset");
        }
        mvc.perform(put("/api/zdmj/users/password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2003));
        verification.sendVerificationCode(user.getEmail(), "reset_password");
        String freshBody = resetBody(user.getEmail(), code(key));
        mvc.perform(put("/api/zdmj/users/password").contentType(MediaType.APPLICATION_JSON).content(freshBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        String password = jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, user.getId());
        assertThat(new BCryptPasswordEncoder().matches("changed-password", password)).isTrue();
        assertThat(redis.hasKey(key)).isFalse();
        mvc.perform(put("/api/zdmj/users/password").contentType(MediaType.APPLICATION_JSON).content(freshBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2003));
    }

    @Test
    void 注册接口_正确验证码只可使用一次_错误验证码不改变记录() throws Exception {
        verification.sendVerificationCode(EMAIL, "register");
        String code = code(KEY);
        String record = redis.opsForValue().get(KEY);
        String request = "{\"username\":\"verification-user\",\"email\":\"" + EMAIL
                + "\",\"password\":\"password-123\",\"verificationCode\":\"%s\"}";
        mvc.perform(post("/api/zdmj/users").contentType(MediaType.APPLICATION_JSON).content(request.formatted("000000")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(2003)).andExpect(jsonPath("$.detail").value("验证码不正确"));
        assertThat(redis.opsForValue().get(KEY)).isEqualTo(record);
        mvc.perform(post("/api/zdmj/users").contentType(MediaType.APPLICATION_JSON).content(request.formatted(code)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(redis.hasKey(KEY)).isFalse();
        assertThatThrownBy(() -> verification.verifyAndConsume(EMAIL, code, VerificationCodeScene.REGISTER))
                .isInstanceOf(BusinessException.class).hasMessage("验证码已过期或不存在");
    }

    @Test
    void 验证码场景不匹配_不消费原记录_记录到期后拒绝使用() {
        verification.sendVerificationCode(EMAIL, "register");
        String code = code(KEY);
        String record = redis.opsForValue().get(KEY);
        assertThatThrownBy(() -> verification.verifyAndConsume(EMAIL, code, VerificationCodeScene.RESET_PASSWORD))
                .isInstanceOf(BusinessException.class).hasMessage("验证码已过期或不存在");
        assertThat(redis.opsForValue().get(KEY)).isEqualTo(record);
        redis.expire(KEY, Duration.ZERO);
        assertThatThrownBy(() -> verification.verifyAndConsume(EMAIL, code, VerificationCodeScene.REGISTER))
                .isInstanceOf(BusinessException.class).hasMessage("验证码已过期或不存在");
    }

    @Test
    void 同一验证码并发消费_数据库写入尚未结束_其他请求已被拒绝() throws Exception {
        verification.sendVerificationCode(EMAIL, "register");
        String code = code(KEY);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> {
                verification.verifyAndConsume(EMAIL, code, VerificationCodeScene.REGISTER);
                writing.countDown();
                await(finish);
                return jdbc.update("INSERT INTO users(username,email,password) VALUES (?,?,?)", "verified-user", EMAIL, "hash");
            });
            try {
                assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(redis.hasKey(KEY)).isFalse();
                assertThatThrownBy(() -> verification.verifyAndConsume(EMAIL, code, VerificationCodeScene.REGISTER))
                        .isInstanceOf(BusinessException.class).hasMessage("验证码已过期或不存在");
            } finally {
                finish.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, EMAIL)).isEqualTo(1);
    }

    private String code(String key) {
        String record = redis.opsForValue().get(key);
        assertThat(record).matches("[0-9a-f-]{36}:[1-9][0-9]{5}");
        return record.substring(record.lastIndexOf(':') + 1);
    }

    private UserRegisterRequest registration(String username, String code) {
        var request = new UserRegisterRequest();
        request.setUsername(username);
        request.setEmail(EMAIL);
        request.setPassword("password-123");
        request.setVerificationCode(code);
        return request;
    }

    private String resetBody(String email, String code) {
        return "{\"email\":\"" + email + "\",\"verificationCode\":\"" + code + "\",\"newPassword\":\"changed-password\"}";
    }

    private VerificationCodeServiceImpl service(EmailService mail) {
        return new VerificationCodeServiceImpl(redis, mail);
    }

    private static void await(CountDownLatch signal) {
        try {
            if (!signal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试等待超时");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", failure);
        }
    }
}
