package com.zdmj.integration.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.async.LlmStreamConsumer;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

/**
 * 异步集成测试的入队夹具与 Redis Stream 查询。
 */
final class AsyncTaskSupport {

    final ApiClient api;
    final JdbcTemplate jdbcTemplate;
    final RedisUtil redisUtil;
    final StringRedisTemplate stringRedisTemplate;
    final LlmStreamConsumer consumer;
    private final TestUsers testUsers;
    private final TestJwt testJwt;

    AsyncTaskSupport(MockMvc mockMvc, TestUsers testUsers, TestJwt testJwt, JdbcTemplate jdbcTemplate,
            RedisUtil redisUtil, StringRedisTemplate stringRedisTemplate, AsyncLlmTaskMapper asyncLlmTaskMapper,
            List<AsyncTaskExecutor> executors) {
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
        this.redisUtil = redisUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.consumer = new LlmStreamConsumer(redisUtil, asyncLlmTaskMapper, executors);
    }

    /**
     * 准备可成功执行的人岗匹配任务，并停在数据库 {@code PENDING}、消息已入 Stream。
     */
    Prepared enqueueMatch() throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        User owner = pair.userA();
        String ownerToken = testJwt.authorize(owner);
        String otherToken = testJwt.authorize(pair.userB());
        long skillId = ApiClient.dataId(api.post(ownerToken, "/skills",
                "{\"content\":[{\"type\":\"编程语言\",\"content\":[\"Java\"]}]}"));
        ApiClient.requireOk(api.post(ownerToken, "/resumes", "{\"skillId\":" + skillId + "}"));
        long jobId = ApiClient.dataId(api.post(ownerToken, "/jobs", """
                {
                  "jobName": "后端工程师",
                  "companyName": "星云科技",
                  "description": "负责服务端开发",
                  "location": "上海",
                  "salaryMin": 20000,
                  "salaryMax": 30000,
                  "salaryType": 2
                }
                """));
        jdbcTemplate.update("INSERT INTO student_capability_profiles (user_id, professional_skills) VALUES (?, ?)",
                owner.getId(), "掌握 Java");
        jdbcTemplate.update("INSERT INTO job_capability_profiles (job_id, professional_skills) VALUES (?, ?)",
                jobId, "要求 Java");
        long taskId = ApiClient.dataLong(api.post(ownerToken, "/matches/jobs/" + jobId, "{}"), "$.data.taskId");
        return new Prepared(owner, ownerToken, otherToken, jobId, taskId);
    }

    int status(long taskId) {
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM async_llm_tasks WHERE id = ?", Integer.class, taskId);
        if (status == null) {
            throw new IllegalStateException("任务不存在: " + taskId);
        }
        return status;
    }

    long streamSize() {
        Long size = stringRedisTemplate.opsForStream().size(RedisConstants.LLM_STREAM_KEY);
        if (size == null) {
            throw new IllegalStateException("XLEN 返回空");
        }
        return size;
    }

    long pendingCount() {
        PendingMessagesSummary summary = stringRedisTemplate.opsForStream()
                .pending(RedisConstants.LLM_STREAM_KEY, RedisConstants.LLM_STREAM_GROUP);
        if (summary == null) {
            throw new IllegalStateException("XPENDING 返回空");
        }
        return summary.getTotalPendingMessages();
    }

    void assertGroupExists() {
        StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(RedisConstants.LLM_STREAM_KEY);
        assertThat(groups.stream().map(StreamInfo.XInfoGroup::groupName))
                .contains(RedisConstants.LLM_STREAM_GROUP);
    }

    int matchCount(long userId, long jobId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_student_matches WHERE user_id = ? AND job_id = ?",
                Integer.class, userId, jobId);
        return count == null ? 0 : count;
    }

    void assertStatus(long taskId, AsyncTaskStatus expected) {
        assertThat(status(taskId)).isEqualTo(expected.getCode());
    }

    record Prepared(User owner, String ownerToken, String otherToken, long jobId, long taskId) {
    }
}
