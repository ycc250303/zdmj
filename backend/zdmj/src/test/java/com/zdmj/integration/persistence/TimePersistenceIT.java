package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.MutableClock;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

@AutoConfigureMockMvc
class TimePersistenceIT extends IntegrationTestBase {

    private final MutableClock mutableClock;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final ApiClient api;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TimePersistenceIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MutableClock mutableClock,
            TestUsers testUsers, TestJwt testJwt, MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.mutableClock = mutableClock;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.api = new ApiClient(mockMvc);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 用户时间_固定时钟写入后再更新_创建时间不变且更新时间前进() throws Exception {
        Instant issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        mutableClock.setInstant(issued);
        User user = testUsers.createPair().userA();
        LocalDateTime created = LocalDateTime.ofInstant(issued, java.time.ZoneId.of("Asia/Shanghai"));
        assertThat(read(user.getId(), "created_at")).isEqualTo(created);
        assertThat(read(user.getId(), "updated_at")).isEqualTo(created);

        Instant updated = issued.plus(2, ChronoUnit.MINUTES);
        mutableClock.setInstant(updated);
        ApiClient.requireOk(api.put(testJwt.authorize(user), "/users/me", "{\"name\":\"新姓名\"}"));
        assertThat(read(user.getId(), "created_at")).isEqualTo(created);
        assertThat(read(user.getId(), "updated_at"))
                .isEqualTo(LocalDateTime.ofInstant(updated, java.time.ZoneId.of("Asia/Shanghai")));
    }

    private LocalDateTime read(long userId, String column) {
        Timestamp value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM users WHERE id = ?", Timestamp.class, userId);
        if (value == null) {
            throw new IllegalStateException("用户时间为空: " + column);
        }
        return value.toLocalDateTime();
    }
}
