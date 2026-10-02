package com.zdmj.integration.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.common.context.UserHolder;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

@AutoConfigureMockMvc
class AuthenticationFlowIT extends IntegrationTestBase {

    private static final String ME = "/api/zdmj/users/me";

    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;

    @Autowired
    AuthenticationFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc,
            TestUsers testUsers, TestJwt testJwt) {
        super(databaseCleaner);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
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

    private static void assertContextCleared() {
        if (UserHolder.get() != null || SecurityContextHolder.getContext().getAuthentication() != null) {
            throw new AssertionError("请求结束后用户上下文或安全上下文仍有残留");
        }
    }
}
