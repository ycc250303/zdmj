package com.zdmj.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

/**
 * 跨用户访问沿用当前错误语义：隐藏资源返回 404，显式无权返回 403。
 * 按当前用户过滤、没有独立资源编号的查询返回空结果。
 */
@AutoConfigureMockMvc
class CrossUserAuthorizationIT extends IntegrationTestBase {

    private final ApiClient api;
    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @Autowired
    CrossUserAuthorizationIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc,
            TestUsers testUsers, TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createUsers() {
        TestUsers.Pair pair = testUsers.createPair();
        userA = pair.userA();
        userB = pair.userB();
        tokenA = testJwt.authorize(userA);
        tokenB = testJwt.authorize(userB);
    }

    @Test
    void 简历_其他用户修改或删除_返回无权且数据库未变化() throws Exception {
        long skillId = ApiClient.dataId(api.post(tokenA, "/skills", skillJson("Java")));
        long resumeId = ApiClient.dataId(api.post(tokenA, "/resumes", "{\"skillId\":" + skillId + "}"));
        String owned = ApiClient.requireOk(api.get(tokenA, "/resumes"));
        assertThat(JsonPath.<Integer>read(owned, "$.data.length()")).isEqualTo(1);

        String hidden = ApiClient.requireOk(api.get(tokenB, "/resumes"));
        assertThat(JsonPath.<Integer>read(hidden, "$.data.length()")).isZero();
        ApiClient.assertProblem(api.put(tokenB, "/resumes",
                "{\"id\":" + resumeId + ",\"skillId\":" + skillId + "}"), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/resumes/" + resumeId), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT skill_id FROM resumes WHERE id = ?", Long.class, resumeId))
                .isEqualTo(skillId);
        assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM resumes WHERE id = ?", Long.class, resumeId))
                .isEqualTo(userA.getId());

        ApiClient.requireOk(api.delete(tokenA, "/resumes/" + resumeId));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM resumes WHERE id = ?", Integer.class, resumeId))
                .isZero();
    }

    @Test
    void 教育经历_其他用户修改或删除_返回无权且学校名称保持不变() throws Exception {
        long id = ApiClient.dataId(api.post(tokenA, "/educations", """
                {"school":"测试大学","major":"计算机","degree":3,"startDate":"2020-09-01","endDate":"2024-06-30"}
                """));
        assertAbsentFromList(tokenB, "/educations");
        ApiClient.assertProblem(api.put(tokenB, "/educations", """
                {"id":%d,"school":"篡改大学"}
                """.formatted(id)), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/educations/" + id), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT school FROM educations WHERE id = ?", String.class, id))
                .isEqualTo("测试大学");
        ApiClient.requireOk(api.delete(tokenA, "/educations/" + id));
    }

    @Test
    void 工作经历_其他用户修改或删除_返回无权且公司名称保持不变() throws Exception {
        long id = ApiClient.dataId(api.post(tokenA, "/career", """
                {"company":"测试公司","position":"实习生","startDate":"2023-07-01"}
                """));
        assertAbsentFromList(tokenB, "/career");
        ApiClient.assertProblem(api.put(tokenB, "/career", """
                {"id":%d,"company":"篡改公司"}
                """.formatted(id)), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/career/" + id), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT company FROM careers WHERE id = ?", String.class, id))
                .isEqualTo("测试公司");
        ApiClient.requireOk(api.delete(tokenA, "/career/" + id));
    }

    @Test
    void 项目经历_其他用户修改或删除_返回无权且项目名称保持不变() throws Exception {
        long id = ApiClient.dataId(api.post(tokenA, "/projects", """
                {"name":"测试项目","startDate":"2023-01-01","role":"开发","description":"实现接口","contribution":"完成后端"}
                """));
        assertAbsentFromList(tokenB, "/projects");
        ApiClient.assertProblem(api.put(tokenB, "/projects", """
                {"id":%d,"name":"篡改项目"}
                """.formatted(id)), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/projects/" + id), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM project_experiences WHERE id = ?", String.class, id))
                .isEqualTo("测试项目");
        ApiClient.requireOk(api.delete(tokenA, "/projects/" + id));
    }

    @Test
    void 获奖信息_其他用户修改或删除_返回无权且奖项名称保持不变() throws Exception {
        long id = ApiClient.dataId(api.post(tokenA, "/awards", """
                {"awardType":2,"name":"测试奖项","awardDate":"2024-05-01"}
                """));
        assertAbsentFromList(tokenB, "/awards");
        ApiClient.assertProblem(api.put(tokenB, "/awards", """
                {"id":%d,"name":"篡改奖项"}
                """.formatted(id)), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/awards/" + id), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM awards WHERE id = ?", String.class, id))
                .isEqualTo("测试奖项");
        ApiClient.requireOk(api.delete(tokenA, "/awards/" + id));
    }

    @Test
    void 技能_其他用户修改或删除_返回无权且技能内容保持不变() throws Exception {
        long id = ApiClient.dataId(api.post(tokenA, "/skills", skillJson("Java")));
        assertAbsentFromList(tokenB, "/skills");
        ApiClient.assertProblem(api.put(tokenB, "/skills", """
                {"id":%d,"content":[{"type":"编程语言","content":["Python"]}]}
                """.formatted(id)), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/skills/" + id), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT content::text FROM skills WHERE id = ?", String.class, id))
                .contains("Java");
        ApiClient.requireOk(api.delete(tokenA, "/skills/" + id));
    }

    @Test
    void 会话_其他用户查询修改或删除_返回无权且标题保持不变() throws Exception {
        long id = ApiClient.dataId(api.post(tokenA, "/conversations",
                "{\"config\":{\"useSystemKnowledge\":false,\"ragDocumentIds\":[]}}"));
        ApiClient.requireOk(api.put(tokenA, "/conversations/" + id + "/title?title=原标题", null));
        String hidden = ApiClient.requireOk(api.get(tokenB, "/conversations"));
        assertThat(JsonPath.<Integer>read(hidden, "$.data.length()")).isZero();
        ApiClient.assertProblem(api.get(tokenB, "/conversations/" + id), 403, 1003);
        ApiClient.assertProblem(api.put(tokenB, "/conversations/" + id + "/title?title=篡改标题", null), 403, 1003);
        ApiClient.assertProblem(api.get(tokenB, "/messages?conversationId=" + id), 403, 1003);
        ApiClient.assertProblem(api.delete(tokenB, "/conversations/" + id), 403, 1003);
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM conversations WHERE id = ?", String.class, id))
                .isEqualTo("原标题");
        ApiClient.requireOk(api.delete(tokenA, "/conversations/" + id));
    }

    @Test
    void 知识文档_其他用户查询修改或删除_返回不存在且标题保持不变() throws Exception {
        api.post(tokenA, "/knowledge", null);
        String url = upload(tokenA, "owned.md", "归属文档");
        long id = ApiClient.dataId(api.post(tokenA, "/knowledge-document", """
                {"title":"原标题","type":1,"content":"%s"}
                """.formatted(url)));
        String hidden = ApiClient.requireOk(api.get(tokenB, "/knowledge-document?page=1&limit=20"));
        assertThat(JsonPath.<Integer>read(hidden, "$.data.total")).isZero();
        ApiClient.assertProblem(api.get(tokenB, "/knowledge-document/" + id), 404, 8012);
        ApiClient.assertProblem(api.put(tokenB, "/knowledge-document", """
                {"id":%d,"title":"篡改标题","type":2,"content":"https://github.com/example/repo"}
                """.formatted(id)), 404, 8012);
        ApiClient.assertProblem(api.delete(tokenB, "/knowledge-document/" + id), 404, 8012);
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM knowledge_documents WHERE id = ?", String.class, id))
                .isEqualTo("原标题");
        ApiClient.requireOk(api.delete(tokenA, "/knowledge-document/" + id));
    }

    @Test
    void 匹配结果_其他用户查询_结果为空且分数保持不变() throws Exception {
        long jobId = createJob(tokenA);
        jdbcTemplate.update("""
                INSERT INTO job_student_matches (user_id, job_id, overall_score, summary)
                VALUES (?, ?, 80, '固定匹配摘要')
                """, userA.getId(), jobId);
        String owned = ApiClient.requireOk(api.get(tokenA, "/matches/jobs/" + jobId));
        assertThat(JsonPath.<Integer>read(owned, "$.data.overallScore")).isEqualTo(80);
        String hidden = ApiClient.requireOk(api.get(tokenB, "/matches/jobs/" + jobId));
        assertThat(JsonPath.read(hidden, "$.data") == null).isTrue();
        String page = ApiClient.requireOk(api.get(tokenB, "/matches?page=1&limit=20"));
        assertThat(JsonPath.<Integer>read(page, "$.data.total")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT overall_score FROM job_student_matches WHERE user_id = ? AND job_id = ?",
                Integer.class, userA.getId(), jobId)).isEqualTo(80);
    }

    @Test
    void 职业发展报告_其他用户查询或保存_查询为空且保存返回不存在() throws Exception {
        long jobId = createJob(tokenA);
        long reportId = jdbcTemplate.queryForObject("""
                INSERT INTO career_development_reports (user_id, job_id, report_content)
                VALUES (?, ?, '{"owner":"A"}'::jsonb)
                RETURNING id
                """, Long.class, userA.getId(), jobId);
        String owned = ApiClient.requireOk(api.get(tokenA, "/career-reports/jobs/" + jobId));
        assertThat(JsonPath.<Number>read(owned, "$.data.id").longValue()).isEqualTo(reportId);
        String hidden = ApiClient.requireOk(api.get(tokenB, "/career-reports/jobs/" + jobId));
        assertThat(JsonPath.read(hidden, "$.data") == null).isTrue();
        int before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM career_development_reports WHERE user_id = ? AND job_id = ?",
                Integer.class, userA.getId(), jobId);
        ApiClient.assertProblem(api.put(tokenB, "/career-reports/" + reportId, """
                {"reportContent":{"owner":"B"}}
                """), 404, 12001);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM career_development_reports WHERE user_id = ? AND job_id = ?",
                Integer.class, userA.getId(), jobId)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT report_content::text FROM career_development_reports WHERE id = ?", String.class, reportId))
                .contains("A");
    }

    @Test
    void 用户模型配置_其他用户保存或删除_只影响自己的配置() throws Exception {
        ApiClient.requireOk(api.put(tokenA, "/users/llm-config", """
                {"modelCode":"deepseek-v4-flash","apiKey":"sk-user-a-secret"}
                """));
        String owned = ApiClient.requireOk(api.get(tokenA, "/users/llm-config"));
        assertThat(JsonPath.<Boolean>read(owned, "$.data.configured")).isTrue();
        assertThat(JsonPath.<String>read(owned, "$.data.modelCode")).isEqualTo("deepseek-v4-flash");
        assertThat(owned).doesNotContain("sk-user-a-secret");

        String hidden = ApiClient.requireOk(api.get(tokenB, "/users/llm-config"));
        assertThat(JsonPath.<Boolean>read(hidden, "$.data.configured")).isFalse();

        String cipherA = jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, userA.getId());
        ApiClient.requireOk(api.put(tokenB, "/users/llm-config", """
                {"modelCode":"deepseek-v4-flash","apiKey":"sk-user-b-secret"}
                """));
        ApiClient.requireOk(api.delete(tokenB, "/users/llm-config"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, userA.getId()))
                .isEqualTo(cipherA);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_llm_config WHERE user_id = ?", Integer.class, userB.getId())).isZero();
    }

    @Test
    void 上传文件_其他用户删除_返回无权且文件仍可列出() throws Exception {
        String key = uploadKey(tokenA, "owned.txt", "用户A的文件");
        String listed = ApiClient.requireOk(api.get(tokenA, "/files/list?prefix=files"));
        assertThat(JsonPath.<String>read(listed, "$.data[0].key")).isEqualTo(key);
        String hidden = ApiClient.requireOk(api.get(tokenB, "/files/list?prefix=files"));
        assertThat(JsonPath.<Integer>read(hidden, "$.data.length()")).isZero();
        ApiClient.assertProblem(deleteFile(tokenB, key), 403, 1003);
        String stillThere = ApiClient.requireOk(api.get(tokenA, "/files/list?prefix=files"));
        assertThat(JsonPath.<String>read(stillThere, "$.data[0].key")).isEqualTo(key);
        ApiClient.requireOk(deleteFile(tokenA, key));
        String after = ApiClient.requireOk(api.get(tokenA, "/files/list?prefix=files"));
        assertThat(JsonPath.<Integer>read(after, "$.data.length()")).isZero();
    }

    private void assertAbsentFromList(String token, String path) throws Exception {
        String body = ApiClient.requireOk(api.get(token, path));
        assertThat(JsonPath.<Integer>read(body, "$.data.length()")).isZero();
    }

    private long createJob(String token) throws Exception {
        return ApiClient.dataId(api.post(token, "/jobs", """
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
    }

    private String upload(String token, String fileName, String text) throws Exception {
        MvcResult result = uploadResult(token, fileName, text);
        return JsonPath.read(ApiClient.requireOk(result), "$.data.url");
    }

    private String uploadKey(String token, String fileName, String text) throws Exception {
        MvcResult result = uploadResult(token, fileName, text);
        return JsonPath.read(ApiClient.requireOk(result), "$.data.key");
    }

    private MvcResult deleteFile(String token, String key) throws Exception {
        return api.exchange(delete(ApiClient.PREFIX + "/files").param("key", key), token, null);
    }

    private MvcResult uploadResult(String token, String fileName, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain",
                text.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart(ApiClient.PREFIX + "/files/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
    }

    private static String skillJson(String item) {
        return "{\"content\":[{\"type\":\"编程语言\",\"content\":[\"" + item + "\"]}]}";
    }
}
