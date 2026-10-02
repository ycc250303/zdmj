package com.zdmj.integration.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.async.AsyncStreamDriver;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.integration.async.AsyncTaskSupport.Prepared;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class AsyncTaskIdempotencyIT extends IntegrationTestBase {

    private final AsyncTaskSupport support;

    @Autowired
    AsyncTaskIdempotencyIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate, RedisUtil redisUtil, StringRedisTemplate stringRedisTemplate,
            AsyncLlmTaskMapper asyncLlmTaskMapper, List<AsyncTaskExecutor> executors) {
        super(databaseCleaner);
        this.support = new AsyncTaskSupport(mockMvc, testUsers, testJwt, jdbcTemplate, redisUtil,
                stringRedisTemplate, asyncLlmTaskMapper, executors);
    }

    @Test
    void 人岗匹配_进行中重复提交_返回同一任务且只写一条消息() throws Exception {
        Prepared prepared = support.enqueueMatch();
        long again = ApiClient.dataLong(
                support.api.post(prepared.ownerToken(), "/matches/jobs/" + prepared.jobId(), "{}"),
                "$.data.taskId");

        assertThat(again).isEqualTo(prepared.taskId());
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.PENDING);
        assertThat(support.streamSize()).isEqualTo(1);
        assertThat(support.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_llm_tasks WHERE user_id = ? AND task_type = 4",
                Integer.class, prepared.owner().getId())).isEqualTo(1);
    }

    @Test
    void 人岗匹配_重复消息与完成后再次消费_只生成一条匹配且确认消息() throws Exception {
        Prepared prepared = support.enqueueMatch();
        support.redisUtil.xaddTask(RedisConstants.LLM_STREAM_KEY, prepared.taskId());
        assertThat(support.streamSize()).isEqualTo(2);

        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(support.redisUtil));
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.SUCCESS);
        assertThat(support.matchCount(prepared.owner().getId(), prepared.jobId())).isEqualTo(1);

        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(support.redisUtil));
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.SUCCESS);
        assertThat(support.matchCount(prepared.owner().getId(), prepared.jobId())).isEqualTo(1);

        support.redisUtil.xaddTask(RedisConstants.LLM_STREAM_KEY, prepared.taskId());
        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(support.redisUtil));

        support.assertStatus(prepared.taskId(), AsyncTaskStatus.SUCCESS);
        assertThat(support.jdbcTemplate.queryForObject(
                "SELECT error_message FROM async_llm_tasks WHERE id = ?",
                String.class, prepared.taskId())).isNull();
        assertThat(support.matchCount(prepared.owner().getId(), prepared.jobId())).isEqualTo(1);
        assertThat(support.pendingCount()).isZero();
    }
}
