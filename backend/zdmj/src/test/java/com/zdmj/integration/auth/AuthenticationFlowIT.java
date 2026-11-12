package com.zdmj.integration.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestMailSender;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.enums.VerificationCodeScene;

@AutoConfigureMockMvc
class AuthenticationFlowIT extends IntegrationTestBase {

    private static final String ME = "/api/zdmj/users/me";
    private static final String VERIFICATION_CODES = "/api/zdmj/users/verification-codes";

    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final StringRedisTemplate redisTemplate;
    private final TestMailSender mailSender;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuthenticationFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc,
            TestUsers testUsers, TestJwt testJwt, StringRedisTemplate redisTemplate, TestMailSender mailSender, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.jdbcTemplate = jdbcTemplate;
    }

    @ParameterizedTest
    @CsvSource({"true, register, REGISTER, 注册验证码", "false, reset_password, RESET_PASSWORD, 重置密码验证码"})
    void 发送验证码_按类型发送邮件_有效期内拒绝重复发送(
            boolean registered, String type, VerificationCodeScene scene, String subject) throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        String email = registered ? pair.userA().getEmail() : "new-user@example.com";
        int sentBefore = mailSender.sentMessages().size();
        var request = post(VERIFICATION_CODES).param("email", email).param("type", type);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("验证码已发送到邮箱"));

        String key = scene.key(email);
        String record = redisTemplate.opsForValue().get(key);
        assertTrue(record != null && record.matches("[0-9a-f-]{36}:[1-9][0-9]{5}"));
        String code = record.substring(record.lastIndexOf(':') + 1);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= VerificationCodeScene.TTL_SECONDS);
        VerificationCodeScene other = scene == VerificationCodeScene.REGISTER
                ? VerificationCodeScene.RESET_PASSWORD : VerificationCodeScene.REGISTER;
        assertNull(redisTemplate.opsForValue().get(other.key(email)));
        assertEquals(sentBefore + 1, mailSender.sentMessages().size());
        var mail = mailSender.sentMessages().get(sentBefore);
        assertEquals(subject, mail.getSubject());
        assertEquals(email, mail.getAllRecipients()[0].toString());
        assertTrue(mailContains(mail, code));

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(2009));
        assertEquals(record, redisTemplate.opsForValue().get(key));
        assertEquals(sentBefore + 1, mailSender.sentMessages().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "invalid", "reset", "reset-password", "RESET_PASSWORD"})
    void 发送验证码_类型为空白或非法_返回参数错误且不发送邮件(String type) throws Exception {
        String email = "new-user@example.com";
        int sentBefore = mailSender.sentMessages().size();

        mockMvc.perform(post(VERIFICATION_CODES).param("email", email).param("type", type))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.detail").value(type.isBlank() ? "type 不能为空" : "type 无效"));

        assertEquals(sentBefore, mailSender.sentMessages().size());
        for (VerificationCodeScene scene : VerificationCodeScene.values()) {
            assertNull(redisTemplate.opsForValue().get(scene.key(email)));
        }
    }

    @Test
    void 发送验证码_未传类型_返回参数错误且不发送邮件() throws Exception {
        String email = "new-user@example.com";
        int sentBefore = mailSender.sentMessages().size();

        mockMvc.perform(post(VERIFICATION_CODES).param("email", email))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertEquals(sentBefore, mailSender.sentMessages().size());
        for (VerificationCodeScene scene : VerificationCodeScene.values()) {
            assertNull(redisTemplate.opsForValue().get(scene.key(email)));
        }
    }

    @Test
    void 连续请求_同一线程先后使用两名用户令牌_响应身份与令牌一致且请求结束后上下文已清空() throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        String tokenA = testJwt.authorize(pair.userA());
        String tokenB = testJwt.authorize(pair.userB());

        updateName(tokenA, pair.userA(), "用户A-已认证");
        assertContextCleared();
        updateName(tokenB, pair.userB(), "用户B-已认证");
        assertContextCleared();
    }

    @Test
    void 客户端传入身份字段_仍使用认证主体且其他用户资料不变() throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        String token = testJwt.authorize(pair.userA());

        mockMvc.perform(put(ME)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("userId", pair.userB().getId().toString())
                        .param("actor.userId", pair.userB().getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"认证用户本人\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(pair.userA().getId()));

        assertEquals("认证用户本人", jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, pair.userA().getId()));
        assertEquals(pair.userB().getName(), jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, pair.userB().getId()));
        assertContextCleared();
    }

    @Test
    void 并发请求_两名用户同时携带各自令牌_响应身份互不串用() throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        String tokenA = testJwt.authorize(pair.userA());
        String tokenB = testJwt.authorize(pair.userB());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> futureA = pool.submit(() -> updateWhenStarted(
                    ready, start, tokenA, pair.userA(), "用户A-并发"));
            Future<MvcResult> futureB = pool.submit(() -> updateWhenStarted(
                    ready, start, tokenB, pair.userB(), "用户B-并发"));
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发请求未能在时限内准备完成");
            }
            start.countDown();
            assertIdentity(futureA.get(5, TimeUnit.SECONDS), pair.userA(), "用户A-并发");
            assertIdentity(futureB.get(5, TimeUnit.SECONDS), pair.userB(), "用户B-并发");
        } finally {
            pool.shutdownNow();
        }
    }

    private void updateName(String token, User user, String name) throws Exception {
        MvcResult result = mockMvc.perform(put(ME)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(content().string(not(containsString(token))))
                .andReturn();
        assertIdentity(result, user, name);
    }

    private MvcResult updateWhenStarted(CountDownLatch ready, CountDownLatch start, String token, User user,
            String name) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发请求未能在时限内开始");
        }
        MvcResult result = mockMvc.perform(put(ME)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        assertContextCleared();
        return result;
    }

    private static void assertIdentity(MvcResult result, User user, String name) throws Exception {
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("更新当前用户失败: " + result.getResponse().getStatus()
                    + " " + result.getResponse().getContentAsString());
        }
        String body = result.getResponse().getContentAsString();
        List<String> unexpected = new ArrayList<>();
        if (!user.getId().equals(JsonPath.<Number>read(body, "$.data.id").longValue())) {
            unexpected.add("id");
        }
        if (!name.equals(JsonPath.<String>read(body, "$.data.name"))) {
            unexpected.add("name");
        }
        if (!unexpected.isEmpty()) {
            throw new AssertionError("响应身份与令牌不一致: " + unexpected + " body=" + body);
        }
    }

    private static boolean mailContains(Part part, String text) throws Exception {
        Object content = part.getContent();
        if (content instanceof String body) {
            return body.contains(text);
        }
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                if (mailContains(multipart.getBodyPart(index), text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertContextCleared() {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            throw new AssertionError("请求结束后安全上下文仍有残留");
        }
    }
}
