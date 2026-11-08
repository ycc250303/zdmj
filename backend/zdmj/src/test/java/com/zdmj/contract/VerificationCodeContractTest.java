package com.zdmj.contract;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.config.WebMvcConfig;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.userAuthService.controller.UserController;
import com.zdmj.userAuthService.enums.VerificationCodeError;
import com.zdmj.userAuthService.service.UserLoginService;
import com.zdmj.userAuthService.service.VerificationCodeService;

@WebMvcTest(controllers = UserController.class, useDefaultFilters = false)
@Import({UserController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class VerificationCodeContractTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private UserLoginService users;
    @MockitoBean private VerificationCodeService verification;

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"CAPTCHA_STORE_UNAVAILABLE", "CAPTCHA_MAIL_UNAVAILABLE"})
    void 发送验证码基础设施故障_返回503与独立错误码_不泄露内部原因(ErrorCode error) throws Exception {
        doThrow(new BusinessException(error, new IllegalStateException("private-failure-reason")))
                .when(verification).sendVerificationCode("test@example.com", "register");
        mvc.perform(post("/api/zdmj/users/verification-codes").param("email", "test@example.com").param("type", "register"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(error.getCode()))
                .andExpect(jsonPath("$.detail").value(error.getMessage()))
                .andExpect(content().string(not(containsString("private-failure-reason"))));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 注册验证码被拒绝_返回2003及具体原因(long scriptResult) throws Exception {
        var failure = VerificationCodeError.rejection(scriptResult);
        doThrow(failure).when(users).register(any());
        mvc.perform(post("/api/zdmj/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"test-user","email":"test@example.com","password":"test-password","verificationCode":"123456"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(jsonPath("$.detail").value(failure.getMessage()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 注册或重置密码Redis故障_返回503且保留存储错误码(boolean register) throws Exception {
        var failure = new BusinessException(ErrorCode.CAPTCHA_STORE_UNAVAILABLE,
                new RedisConnectionFailureException("private-redis-failure"));
        if (register) {
            doThrow(failure).when(users).register(any());
        } else {
            doThrow(failure).when(users).resetPassword(any());
        }
        var request = register ? post("/api/zdmj/users") : put("/api/zdmj/users/password");
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"test-user","email":"test@example.com","password":"test-password",
                "newPassword":"test-password","verificationCode":"123456"}
                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2015))
                .andExpect(content().string(not(containsString("private-redis-failure"))));
    }
}
