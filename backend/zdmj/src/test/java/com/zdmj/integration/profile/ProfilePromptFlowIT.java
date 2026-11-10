package com.zdmj.integration.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

/** 验证画像接口在同步请求内完成方向识别、提示词加载和数据库保存。 */
@AutoConfigureMockMvc
class ProfilePromptFlowIT extends IntegrationTestBase {

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ProfilePromptFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 岗位画像_模型识别后_保存规范方向与专属提示词() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = ApiClient.dataId(api.post(token, "/jobs", """
                {"jobName":"后端工程师","companyName":"测试企业","description":"负责应用开发",
                 "location":"上海","salaryType":2,"salaryMin":10000,"salaryMax":20000}
                """));
        MvcResult generated = api.post(token, "/jobs/" + jobId + "/capability-profile", "{}");
        assertThat(JsonPath.<String>read(generated.getResponse().getContentAsString(), "$.data.professionalSkills"))
                .isEqualTo("Java 岗位要求");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_role_type FROM job_capability_profiles WHERE job_id = ?", String.class, jobId))
                .isEqualTo("java-backend");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT prompt_name FROM job_capability_profiles WHERE job_id = ?", String.class, jobId))
                .isEqualTo("job-requirement/java-backend");
        String body = ApiClient.requireOk(api.get(token, "/jobs/capability-profile?id=" + jobId));
        assertThat(JsonPath.<String>read(body, "$.data.professionalSkills")).isEqualTo("Java 岗位要求");
    }

    @Test
    void 人岗匹配_学生画像缺失_不生成岗位画像或调用模型() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long jobId = ApiClient.dataId(api.post(token, "/jobs", """
                {"jobName":"后端工程师","companyName":"测试企业","description":"Java 开发",
                 "location":"上海","salaryType":2,"salaryMin":10000,"salaryMax":20000}
                """));
        com.zdmj.testsupport.ExternalAiStubConfig.clearModelTransactionObservations();

        ApiClient.assertProblem(api.post(token, "/matches/jobs/" + jobId, "{}"), 400, 11002);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_capability_profiles WHERE job_id = ?", Integer.class, jobId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_student_matches", Integer.class)).isZero();
        assertThat(com.zdmj.testsupport.ExternalAiStubConfig.modelTransactionObservations()).isEmpty();
    }

    @Test
    void 简历画像_关键词识别后_保存规范方向与专属提示词() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        MvcResult generated = api.post(token, "/capability-profile/generate",
                "{\"rawText\":\"Java Spring Boot MySQL Redis 项目经验\"}");
        assertThat(JsonPath.<String>read(generated.getResponse().getContentAsString(), "$.data.professionalSkills"))
                .isEqualTo("Java 简历能力");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_role_type FROM student_capability_profiles WHERE user_id = ?", String.class, owner.getId()))
                .isEqualTo("java-backend");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT prompt_name FROM student_capability_profiles WHERE user_id = ?", String.class, owner.getId()))
                .isEqualTo("resume-analysis/java-backend");
        String body = ApiClient.requireOk(api.get(token, "/capability-profile/current/query"));
        assertThat(JsonPath.<String>read(body, "$.data.professionalSkills")).isEqualTo("Java 简历能力");
    }
}
