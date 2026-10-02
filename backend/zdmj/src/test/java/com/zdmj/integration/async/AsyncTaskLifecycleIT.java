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
import com.zdmj.common.util.RedisUtil;
import com.zdmj.integration.async.AsyncTaskSupport.Prepared;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.ExternalAiStubConfig;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class AsyncTaskLifecycleIT extends IntegrationTestBase {

    private final AsyncTaskSupport support;
    private final AsyncLlmTaskMapper asyncLlmTaskMapper;

    @Autowired
    AsyncTaskLifecycleIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate, RedisUtil redisUtil, StringRedisTemplate stringRedisTemplate,
            AsyncLlmTaskMapper asyncLlmTaskMapper, List<AsyncTaskExecutor> executors) {
        super(databaseCleaner);
        this.asyncLlmTaskMapper = asyncLlmTaskMapper;
        this.support = new AsyncTaskSupport(mockMvc, testUsers, testJwt, jdbcTemplate, redisUtil,
                stringRedisTemplate, asyncLlmTaskMapper, executors);
    }

    @Test
    void 人岗匹配_入队后消费_状态经执行中到成功且可查询结果() throws Exception {
        Prepared prepared = support.enqueueMatch();
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.PENDING);

        MapRecord<String, String, String> record = AsyncStreamDriver.readNext(support.redisUtil);
        assertThat(asyncLlmTaskMapper.claimPendingTask(prepared.taskId())).isEqualTo(1);
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.RUNNING);
        assertThat(support.jdbcTemplate.queryForObject(
                "SELECT started_at IS NOT NULL FROM async_llm_tasks WHERE id = ?",
                Boolean.class, prepared.taskId())).isTrue();

        AsyncStreamDriver.consume(support.consumer, record);

        support.assertStatus(prepared.taskId(), AsyncTaskStatus.SUCCESS);
        assertThat(support.jdbcTemplate.queryForObject(
                "SELECT completed_at IS NOT NULL FROM async_llm_tasks WHERE id = ?",
                Boolean.class, prepared.taskId())).isTrue();
        assertThat(support.pendingCount()).isZero();

        String taskBody = ApiClient.requireOk(support.api.get(prepared.ownerToken(),
                "/async-tasks/" + prepared.taskId()));
        assertThat(JsonPath.<Integer>read(taskBody, "$.data.status")).isEqualTo(AsyncTaskStatus.SUCCESS.getCode());
        assertThat(JsonPath.<Integer>read(taskBody, "$.data.taskType")).isEqualTo(4);
        assertThat((Object) JsonPath.read(taskBody, "$.data.errorMessage")).isNull();
        assertThat((Object) JsonPath.read(taskBody, "$.data.result")).isNull();

        String matchBody = ApiClient.requireOk(support.api.get(prepared.ownerToken(),
                "/matches/jobs/" + prepared.jobId()));
        assertThat(JsonPath.<Integer>read(matchBody, "$.data.overallScore"))
                .isEqualTo(ExternalAiStubConfig.FIXED_MATCH_SCORE);
        assertThat(JsonPath.<String>read(matchBody, "$.data.summary"))
                .isEqualTo(ExternalAiStubConfig.FIXED_MATCH_SUMMARY);
        assertThat(JsonPath.<Integer>read(matchBody, "$.data.dimensions.basic.score"))
                .isEqualTo(ExternalAiStubConfig.FIXED_MATCH_SCORE);
        assertThat(support.matchCount(prepared.owner().getId(), prepared.jobId())).isEqualTo(1);
    }
}
