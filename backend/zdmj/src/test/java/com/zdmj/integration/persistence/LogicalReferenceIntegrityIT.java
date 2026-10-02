package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class LogicalReferenceIntegrityIT extends IntegrationTestBase {

    private final ApiClient api;
    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LogicalReferenceIntegrityIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc,
            TestUsers testUsers, TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 写入校验_会话或教育经历不存在_拒绝写入且不留下部分数据() throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        String tokenA = testJwt.authorize(pair.userA());
        String tokenB = testJwt.authorize(pair.userB());
        long foreignEducationId = ApiClient.dataId(api.post(tokenB, "/educations", educationJson("用户B大学")));

        MvcResult missingConversation = mockMvc.perform(MockMvcRequestBuilders.post(ApiClient.PREFIX + "/messages/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":999999999,\"message\":\"你好\"}"))
                .andReturn();
        ApiClient.assertProblem(missingConversation, 404, 9003);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM messages", Integer.class)).isZero();

        MvcResult foreignEducation = api.put(tokenA, "/resumes/me/content", contentJson(foreignEducationId));
        ApiClient.assertProblem(foreignEducation, 403, 1003);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resumes WHERE user_id = ?", Integer.class, pair.userA().getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM educations WHERE id = ?", Integer.class, foreignEducationId)).isEqualTo(1);
    }

    @Test
    void 一致性检查_预先构造的孤立记录_能够被发现且合法引用不在结果中() {
        TestUsers.Pair pair = testUsers.createPair();
        long skillId = insertSkill(pair.userA().getId());
        jdbcTemplate.update("INSERT INTO resumes (user_id, skill_id) VALUES (?, ?)", pair.userA().getId(), skillId);
        jdbcTemplate.update("INSERT INTO resumes (user_id, skill_id) VALUES (?, 888888888)", pair.userB().getId());
        jdbcTemplate.update("""
                INSERT INTO jobs (job_name, company_id, company_name, description, location, salary_min, salary_max, salary_type, link)
                VALUES ('孤立岗位', 888888888, '不存在的公司', '描述', '上海', 1, 2, 2, '')
                """);
        jdbcTemplate.update("""
                INSERT INTO knowledge_documents (knowledge_id, user_id, type, content)
                VALUES (888888888, ?, 4, 'orphan-doc')
                """, pair.userA().getId());
        jdbcTemplate.update("""
                INSERT INTO messages (conversation_id, user_id, role, content, sequence)
                VALUES (888888888, ?, 1, 'orphan-message', 1)
                """, pair.userA().getId());

        assertThat(LogicalReferenceChecks.find(jdbcTemplate))
                .extracting(LogicalReferenceChecks.Orphan::sourceTable)
                .containsExactly("messages", "knowledge_documents", "resumes", "jobs");
        assertThat(LogicalReferenceChecks.find(jdbcTemplate))
                .filteredOn(orphan -> "resumes".equals(orphan.sourceTable()))
                .hasSize(1);
    }

    private long insertSkill(long userId) {
        jdbcTemplate.update(
                "INSERT INTO skills (user_id, content) VALUES (?, CAST(? AS jsonb))",
                userId, "[]");
        return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE user_id = ?", Long.class, userId);
    }

    private static String educationJson(String school) {
        return """
                {"school":"%s","major":"计算机","degree":3,"startDate":"2020-09-01","endDate":"2024-06-30"}
                """.formatted(school);
    }

    private static String contentJson(long educationId) {
        return """
                {
                  "skill": {"content": [{"type": "编程语言", "content": ["Java"]}]},
                  "educations": [{"id": %d, "school": "用户B大学", "major": "计算机", "degree": 3, "startDate": "2020-09-01"}],
                  "careers": [],
                  "projects": [],
                  "awards": []
                }
                """.formatted(educationId);
    }
}
