package com.zdmj.integration.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.integration.async.AsyncTaskSupport.Prepared;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class AsyncTaskAuthorizationIT extends IntegrationTestBase {

    private final AsyncTaskSupport support;

    @Autowired
    AsyncTaskAuthorizationIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc,
            TestUsers testUsers, TestJwt testJwt, JdbcTemplate jdbcTemplate, RedisUtil redisUtil,
            StringRedisTemplate stringRedisTemplate, AsyncLlmTaskMapper asyncLlmTaskMapper,
            List<AsyncTaskExecutor> executors) {
        super(databaseCleaner);
        this.support = new AsyncTaskSupport(mockMvc, testUsers, testJwt, jdbcTemplate, redisUtil,
                stringRedisTemplate, asyncLlmTaskMapper, executors);
    }

    @Test
    void 异步任务_未登录查询_返回未登录() throws Exception {
        Prepared prepared = support.enqueueMatch();

        ApiClient.assertProblem(
                support.api.perform(MockMvcRequestBuilders.get(
                        ApiClient.PREFIX + "/async-tasks/" + prepared.taskId())).andReturn(),
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.USER_NOT_LOGIN.getCode());
    }

    @Test
    void 异步任务_其他用户查询_返回不存在且任务归属不变() throws Exception {
        Prepared prepared = support.enqueueMatch();

        String owned = ApiClient.requireOk(support.api.get(prepared.ownerToken(),
                "/async-tasks/" + prepared.taskId()));
        assertThat(JsonPath.<Number>read(owned, "$.data.taskId").longValue()).isEqualTo(prepared.taskId());
        ApiClient.assertProblem(
                support.api.get(prepared.otherToken(), "/async-tasks/" + prepared.taskId()),
                HttpStatus.NOT_FOUND.value(),
                ErrorCode.ASYNC_TASK_NOT_FOUND.getCode());
        assertThat(support.jdbcTemplate.queryForObject(
                "SELECT user_id FROM async_llm_tasks WHERE id = ?", Long.class, prepared.taskId()))
                .isEqualTo(prepared.owner().getId());
    }
}
