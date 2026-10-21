package com.zdmj.integration.async;

import com.zdmj.common.async.AsyncStreamDriver;
import com.zdmj.common.async.AsyncTaskExecutor;
import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.testsupport.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证画像接口经真实队列、方向识别、提示词加载及数据库保存的完整流程。 */
@AutoConfigureMockMvc
class ProfilePromptFlowIT extends IntegrationTestBase {
    @Autowired MockMvc mockMvc;
    @Autowired TestUsers users;
    @Autowired TestJwt jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RedisUtil redis;
    @Autowired StringRedisTemplate template;
    @Autowired AsyncLlmTaskMapper tasks;
    @Autowired List<AsyncTaskExecutor> executors;

    @Autowired
    ProfilePromptFlowIT(DatabaseCleaner cleaner) {
        super(cleaner);
    }

    @Test
    void 岗位画像_模型识别后消费_保存规范方向与专属提示词() throws Exception {
        var owner = users.createPair().userA();
        String token = jwt.authorize(owner);
        var api = new ApiClient(mockMvc);
        long jobId = ApiClient.dataId(api.post(token, "/jobs", """
                {"jobName":"后端工程师","companyName":"测试企业","description":"负责应用开发",
                 "location":"上海","salaryType":2,"salaryMin":10000,"salaryMax":20000}
                """));
        long taskId = ApiClient.dataLong(api.post(token, "/jobs/" + jobId + "/capability-profile", "{}"), "$.data.taskId");
        consume(taskId);
        assertThat(jdbc.queryForObject("SELECT target_role_type FROM job_capability_profiles WHERE job_id = ?", String.class, jobId))
                .isEqualTo("java-backend");
        assertThat(jdbc.queryForObject("SELECT prompt_name FROM job_capability_profiles WHERE job_id = ?", String.class, jobId))
                .isEqualTo("job-requirement/java-backend");
        String body = ApiClient.requireOk(api.get(token, "/jobs/capability-profile?id=" + jobId));
        assertThat(JsonPath.<String>read(body, "$.data.professionalSkills")).isEqualTo("Java 岗位要求");
    }

    @Test
    void 简历画像_关键词识别后消费_保存规范方向与专属提示词() throws Exception {
        var owner = users.createPair().userA();
        String token = jwt.authorize(owner);
        var api = new ApiClient(mockMvc);
        long taskId = ApiClient.dataLong(api.post(token, "/capability-profile/generate",
                "{\"rawText\":\"Java Spring Boot MySQL Redis 项目经验\"}"), "$.data.taskId");
        consume(taskId);
        assertThat(jdbc.queryForObject("SELECT target_role_type FROM student_capability_profiles WHERE user_id = ?", String.class, owner.getId()))
                .isEqualTo("java-backend");
        assertThat(jdbc.queryForObject("SELECT prompt_name FROM student_capability_profiles WHERE user_id = ?", String.class, owner.getId()))
                .isEqualTo("resume-analysis/java-backend");
        String body = ApiClient.requireOk(api.get(token, "/capability-profile/current/query"));
        assertThat(JsonPath.<String>read(body, "$.data.professionalSkills")).isEqualTo("Java 简历能力");
    }

    private void consume(long taskId) {
        var support = new AsyncTaskSupport(mockMvc, users, jwt, jdbc, redis, template, tasks, executors);
        support.assertStatus(taskId, AsyncTaskStatus.PENDING);
        AsyncStreamDriver.consume(support.consumer, AsyncStreamDriver.readNext(redis));
        support.assertStatus(taskId, AsyncTaskStatus.SUCCESS);
        assertThat(support.pendingCount()).isZero();
    }
}
