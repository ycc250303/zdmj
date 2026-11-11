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
    void 用户资料_其他用户查询_返回无权且本人仍可查询() throws Exception {
        ApiClient.assertProblem(api.get(tokenB, "/users/" + userA.getId()), 403, 1003);
        String own = ApiClient.requireOk(api.get(tokenA, "/users/" + userA.getId()));
        assertThat(JsonPath.<Number>read(own, "$.data.id").longValue()).isEqualTo(userA.getId());
        assertThat(JsonPath.<String>read(own, "$.data.email")).isEqualTo(userA.getEmail());
    }

    @Test
    void 整份简历_其他用户提交经历或技能编号_返回无权且原数据保持不变() throws Exception {
        String saved = ApiClient.requireOk(api.put(tokenA, "/resumes/me/content", """
                {
                  "skills": [{"name": "Java"}],
                  "educations": [{"school": "测试大学", "major": "计算机", "degree": 3, "startDate": "2020-09-01", "endDate": "2024-06-30"}],
                  "careers": [{"company": "测试公司", "position": "实习生", "startDate": "2023-07-01"}],
                  "projects": [{"name": "测试项目", "startDate": "2023-01-01", "role": "开发", "description": "实现接口", "contribution": "完成后端"}],
                  "awards": [{"name": "测试奖项", "awardDate": "2024-05-01"}]
                }
                """));
        long educationId = JsonPath.<Number>read(saved, "$.data.educations[0].id").longValue();
        long careerId = JsonPath.<Number>read(saved, "$.data.careers[0].id").longValue();
        long projectId = JsonPath.<Number>read(saved, "$.data.projects[0].id").longValue();
        long awardId = JsonPath.<Number>read(saved, "$.data.awards[0].id").longValue();
        long skillId = JsonPath.<Number>read(saved, "$.data.skills[0].id").longValue();

        String hidden = ApiClient.requireOk(api.get(tokenB, "/resumes/content"));
        assertThat(JsonPath.<Integer>read(hidden, "$.data.length()")).isZero();

        ApiClient.assertProblem(api.put(tokenB, "/resumes/me/content", contentWithEducation(educationId, "篡改大学")),
                403, 1003);
        ApiClient.assertProblem(api.put(tokenB, "/resumes/me/content", contentWithCareer(careerId)), 403, 1003);
        ApiClient.assertProblem(api.put(tokenB, "/resumes/me/content", contentWithProject(projectId)), 403, 1003);
        ApiClient.assertProblem(api.put(tokenB, "/resumes/me/content", contentWithAward(awardId)), 403, 1003);
        ApiClient.assertProblem(api.put(tokenB, "/resumes/me/content", """
                {
                  "skills": [{"id": %d, "name": "Python"}],
                  "educations": [],
                  "careers": [],
                  "projects": [],
                  "awards": []
                }
                """.formatted(skillId)), 403, 1003);

        assertThat(jdbcTemplate.queryForObject("SELECT school FROM educations WHERE id = ?", String.class, educationId))
                .isEqualTo("测试大学");
        assertThat(jdbcTemplate.queryForObject("SELECT company FROM careers WHERE id = ?", String.class, careerId))
                .isEqualTo("测试公司");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM project_experiences WHERE id = ?", String.class, projectId))
                .isEqualTo("测试项目");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM awards WHERE id = ?", String.class, awardId))
                .isEqualTo("测试奖项");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM skills WHERE id = ?", String.class, skillId))
                .isEqualTo("Java");
    }

    private static String contentWithEducation(long id, String school) {
        return """
                {
                  "skills": [{"name": "Java"}],
                  "educations": [{"id": %d, "school": "%s", "major": "计算机", "degree": 3, "startDate": "2020-09-01"}],
                  "careers": [],
                  "projects": [],
                  "awards": []
                }
                """.formatted(id, school);
    }

    private static String contentWithCareer(long id) {
        return """
                {
                  "skills": [{"name": "Java"}],
                  "educations": [],
                  "careers": [{"id": %d, "company": "篡改公司", "position": "实习生", "startDate": "2023-07-01"}],
                  "projects": [],
                  "awards": []
                }
                """.formatted(id);
    }

    private static String contentWithProject(long id) {
        return """
                {
                  "skills": [{"name": "Java"}],
                  "educations": [],
                  "careers": [],
                  "projects": [{"id": %d, "name": "篡改项目", "startDate": "2023-01-01", "role": "开发", "description": "实现接口", "contribution": "完成后端"}],
                  "awards": []
                }
                """.formatted(id);
    }

    private static String contentWithAward(long id) {
        return """
                {
                  "skills": [{"name": "Java"}],
                  "educations": [],
                  "careers": [],
                  "projects": [],
                  "awards": [{"id": %d, "name": "篡改奖项", "awardDate": "2024-05-01"}]
                }
                """.formatted(id);
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
        String url = upload(tokenA, "owned.md", "这份文档归属于当前用户，用于校验其他人不能查看或修改。");
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
}
