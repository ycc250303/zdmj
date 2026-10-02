package com.zdmj.integration.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.common.async.AsyncStreamDriver;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.integration.async.AsyncTaskSupport.Prepared;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class AsyncTaskFailureIT extends IntegrationTestBase {

    private final AsyncTaskSupport support;

    @Autowired
    AsyncTaskFailureIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate, RedisUtil redisUtil, StringRedisTemplate stringRedisTemplate,
            AsyncLlmTaskMapper asyncLlmTaskMapper, List<AsyncTaskExecutor> executors) {
        super(databaseCleaner);
        this.support = new AsyncTaskSupport(mockMvc, testUsers, testJwt, jdbcTemplate, redisUtil,
                stringRedisTemplate, asyncLlmTaskMapper, executors);
    }

    @Test
    void 人岗匹配_消费时学生画像已删除_任务失败且查询返回错误摘要() throws Exception {
        Prepared prepared = support.enqueueMatch();
        support.jdbcTemplate.update("DELETE FROM student_capability_profiles WHERE user_id = ?",
                prepared.owner().getId());

        MapRecord<String, String, String> record = AsyncStreamDriver.readNext(support.redisUtil);
        AsyncStreamDriver.consume(support.consumer, record);

        support.assertStatus(prepared.taskId(), AsyncTaskStatus.FAILED);
        assertThat(support.jdbcTemplate.queryForObject(
                "SELECT error_message FROM async_llm_tasks WHERE id = ?",
                String.class, prepared.taskId()))
                .isEqualTo(ErrorCode.MATCH_PRECONDITION_MISSING.getMessage());
        assertThat(support.pendingCount()).isZero();
        assertThat(support.matchCount(prepared.owner().getId(), prepared.jobId())).isZero();

        String body = ApiClient.requireOk(support.api.get(prepared.ownerToken(),
                "/async-tasks/" + prepared.taskId()));
        assertThat(JsonPath.<Integer>read(body, "$.data.status")).isEqualTo(AsyncTaskStatus.FAILED.getCode());
        assertThat(JsonPath.<String>read(body, "$.data.errorMessage"))
                .isEqualTo(ErrorCode.MATCH_PRECONDITION_MISSING.getMessage());
    }
}
