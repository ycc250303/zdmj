package com.zdmj.integration.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.stream.MapRecord;
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
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class AsyncStreamContractIT extends IntegrationTestBase {

    private final AsyncTaskSupport support;

    @Autowired
    AsyncStreamContractIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate, RedisUtil redisUtil, StringRedisTemplate stringRedisTemplate,
            AsyncLlmTaskMapper asyncLlmTaskMapper, List<AsyncTaskExecutor> executors) {
        super(databaseCleaner);
        this.support = new AsyncTaskSupport(mockMvc, testUsers, testJwt, jdbcTemplate, redisUtil,
                stringRedisTemplate, asyncLlmTaskMapper, executors);
    }

    @Test
    void 任务消息_入队后读取_字段只有任务标识() throws Exception {
        Prepared prepared = support.enqueueMatch();

        MapRecord<String, String, String> record = AsyncStreamDriver.readNext(support.redisUtil);

        assertThat(record.getValue()).containsExactly(
                Map.entry(RedisConstants.STREAM_FIELD_TASK_ID, Long.toString(prepared.taskId())));
    }

    @Test
    void 消费组_重复确保_组存在且再次创建不失败() throws Exception {
        support.enqueueMatch();

        support.redisUtil.ensureConsumerGroup(RedisConstants.LLM_STREAM_KEY, RedisConstants.LLM_STREAM_GROUP);
        support.assertGroupExists();
        support.redisUtil.ensureConsumerGroup(RedisConstants.LLM_STREAM_KEY, RedisConstants.LLM_STREAM_GROUP);
        support.assertGroupExists();
    }

    @Test
    void 待处理消息_读取后确认前挂起_确认后移除() throws Exception {
        Prepared prepared = support.enqueueMatch();

        MapRecord<String, String, String> record = AsyncStreamDriver.readNext(support.redisUtil);
        assertThat(support.pendingCount()).isEqualTo(1);
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.PENDING);

        AsyncStreamDriver.consume(support.consumer, record);

        assertThat(support.pendingCount()).isZero();
        support.assertStatus(prepared.taskId(), AsyncTaskStatus.SUCCESS);
    }

    @Test
    void 非法消息_缺少标识或任务不存在_确认丢弃且不写任务() {
        support.redisUtil.ensureConsumerGroup(RedisConstants.LLM_STREAM_KEY, RedisConstants.LLM_STREAM_GROUP);
        support.redisUtil.xadd(RedisConstants.LLM_STREAM_KEY, Map.of("payload", "missing-task"));
        support.redisUtil.xadd(RedisConstants.LLM_STREAM_KEY, Map.of(RedisConstants.STREAM_FIELD_TASK_ID, "abc"));
        support.redisUtil.xaddTask(RedisConstants.LLM_STREAM_KEY, 9_000_000_000L);

        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(support.redisUtil));
        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(support.redisUtil));
        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(support.redisUtil));

        assertThat(support.pendingCount()).isZero();
        assertThat(support.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM async_llm_tasks", Integer.class))
                .isZero();
    }
}
