package com.zdmj.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.config.WebMvcConfig;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.contract.support.ContractProbeController;
import com.zdmj.userAuthService.util.JwtUtil;

@WebMvcTest(controllers = ContractProbeController.class, useDefaultFilters = false)
@Import({ContractProbeController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class TimeSerializationContractTest {

    private static final String SECRET = "zdmj-test-jwt-secret-0123456789abcdef";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void restoreClock() {
        DateTimeUtil.bind(Clock.system(DateTimeUtil.getDefaultZoneId()));
    }

    @Test
    void 时间响应_固定精度与日期_输出本地时间且不带时区偏移() throws Exception {
        mockMvc.perform(get("/api/zdmj/contract/time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.at").value("2024-09-01T08:30:00.123456"))
                .andExpect(jsonPath("$.data.wholeSecond").value("2024-09-01T08:30:00"))
                .andExpect(jsonPath("$.data.day").value("2024-09-01"));
    }

    @Test
    void 应用时钟_固定到上海时区_当前时间不带偏移() {
        DateTimeUtil.bind(Clock.fixed(Instant.parse("2024-09-01T00:30:00Z"), SHANGHAI));
        org.assertj.core.api.Assertions.assertThat(DateTimeUtil.now())
                .isEqualTo(LocalDateTime.of(2024, 9, 1, 8, 30, 0));
        org.assertj.core.api.Assertions.assertThat(DateTimeUtil.now().toString()).doesNotContain("Z", "+");
    }

    @Test
    void 令牌过期_到期瞬间仍有效_超过一毫秒后失效() {
        JwtUtil.initSecret(SECRET);
        Instant issued = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        DateTimeUtil.bind(Clock.fixed(issued, SHANGHAI));
        String token = JwtUtil.generateToken(7L, "user");
        Date expiration = JwtUtil.getExpirationDateFromToken(token);
        org.assertj.core.api.Assertions.assertThat(expiration).isNotNull();

        DateTimeUtil.bind(Clock.fixed(expiration.toInstant(), SHANGHAI));
        org.assertj.core.api.Assertions.assertThat(JwtUtil.validateToken(token)).isTrue();

        DateTimeUtil.bind(Clock.fixed(expiration.toInstant().plusMillis(1), SHANGHAI));
        org.assertj.core.api.Assertions.assertThat(JwtUtil.validateToken(token)).isFalse();
    }

}
