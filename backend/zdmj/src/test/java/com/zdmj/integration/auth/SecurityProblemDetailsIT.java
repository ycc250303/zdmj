package com.zdmj.integration.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.ProblemDetailSupport;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.testsupport.ToggleJwtSessionStore;
import com.zdmj.userAuthService.entity.User;

@AutoConfigureMockMvc
class SecurityProblemDetailsIT extends IntegrationTestBase {

    private static final String PROTECTED = "/api/zdmj/users/";

    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final ToggleJwtSessionStore sessionStore;

    @Autowired
    SecurityProblemDetailsIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc,
            TestUsers testUsers, TestJwt testJwt, ToggleJwtSessionStore sessionStore) {
        super(databaseCleaner);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.sessionStore = sessionStore;
    }

    @AfterEach
    void restoreSessionStore() {
        sessionStore.markAvailable();
    }

    @Test
    void 受保护接口_缺少令牌_返回401且不包含堆栈() throws Exception {
        mockMvc.perform(get(PROTECTED + "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_LOGIN.getCode()))
                .andExpect(jsonPath("$.title").value(ErrorCode.USER_NOT_LOGIN.getMessage()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.USER_NOT_LOGIN.getMessage()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.msg").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void 受保护接口_令牌无法验签_返回401() throws Exception {
        mockMvc.perform(get(PROTECTED + "1").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_LOGIN.getCode()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("not-a-jwt"))));
    }

    @Test
    void 受保护接口_令牌有效但会话不一致_返回401且不回显令牌() throws Exception {
        User user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        sessionStore.replace(user.getId(), token + "-replaced");

        mockMvc.perform(get(PROTECTED + user.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_LOGIN.getCode()))
                .andExpect(jsonPath("$.title").value(ErrorCode.USER_NOT_LOGIN.getMessage()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString(token))));
    }

    @Test
    void 受保护接口_登录状态存储不可用_返回503且不写成未登录() throws Exception {
        User user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        sessionStore.markUnavailable();
        try {
            mockMvc.perform(get(PROTECTED + user.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(ProblemDetailSupport.PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_STORE_UNAVAILABLE.getCode()))
                    .andExpect(jsonPath("$.title").value(ErrorCode.AUTH_STORE_UNAVAILABLE.getMessage()))
                    .andExpect(jsonPath("$.detail").value(ErrorCode.AUTH_STORE_UNAVAILABLE.getMessage()))
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(content().string(not(containsString(token))))
                    .andExpect(content().string(not(containsString("DataAccess"))))
                    .andExpect(content().string(not(containsString("toggle-store-down"))));
        } finally {
            sessionStore.markAvailable();
        }
    }
}
