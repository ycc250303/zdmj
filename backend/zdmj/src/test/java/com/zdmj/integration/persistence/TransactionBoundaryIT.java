package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.ExternalAiStubConfig;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class TransactionBoundaryIT extends IntegrationTestBase {

    private final ApiClient api;
    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionBoundaryIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 简历保存_中途业务失败_已写入的技能与教育经历回滚() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long skillId = ApiClient.dataId(api.post(token, "/skills", skillJson("Java")));
        ApiClient.requireOk(api.put(token, "/resumes/me/content", contentJson(null, "Java", "测试大学")));

        MvcResult rejected = api.put(token, "/resumes/me/content", contentJson(9_999_999_999L, "Python", "回滚大学"));
        ApiClient.assertProblem(rejected, 404, 6005);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content::text FROM skills WHERE id = ?", String.class, skillId)).contains("Java");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM educations WHERE school = '回滚大学'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM educations WHERE school = '测试大学'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 会话发送_模型调用与事件流期间_不持有写入事务() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long conversationId = ApiClient.dataId(api.post(token, "/conversations", """
                {"config":{"useSystemKnowledge":false,"ragDocumentIds":[]}}
                """));
        ExternalAiStubConfig.clearModelTransactionObservations();

        MvcResult async = mockMvc.perform(post(ApiClient.PREFIX + "/messages/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"conversationId\":" + conversationId + ",\"message\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult streamed = mockMvc.perform(asyncDispatch(async)).andExpect(status().isOk()).andReturn();
        String stream = new String(streamed.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(stream).contains("event:delta");
        assertThat(ExternalAiStubConfig.modelTransactionObservations()).isNotEmpty().allMatch(active -> !active);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messages WHERE conversation_id = ?", Integer.class, conversationId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM messages WHERE conversation_id = ? AND role = 2", String.class, conversationId))
                .isEqualTo(ExternalAiStubConfig.FIXED_REPLY);
        Timestamp created = jdbcTemplate.queryForObject(
                "SELECT created_at FROM messages WHERE conversation_id = ? AND role = 1", Timestamp.class,
                conversationId);
        assertThat(created).isNotNull();
    }

    private static String skillJson(String item) {
        return "{\"content\":[{\"type\":\"编程语言\",\"content\":[\"" + item + "\"]}]}";
    }

    private static String contentJson(Long educationId, String skillItem, String school) {
        String educationIdField = educationId == null ? "" : "\"id\":" + educationId + ",";
        return """
                {
                  "skill": {"content": [{"type": "编程语言", "content": ["%s"]}]},
                  "educations": [{%s"school": "%s", "major": "计算机", "degree": 3, "startDate": "2020-09-01", "endDate": "2024-06-30", "gpa": "3.8"}],
                  "careers": [{"company": "测试公司", "position": "后端实习生", "startDate": "2023-07-01"}],
                  "projects": [{"name": "测试项目", "startDate": "2023-01-01", "role": "开发", "description": "实现接口", "contribution": "完成后端"}],
                  "awards": [{"awardType": 2, "name": "测试奖项", "awardDate": "2024-05-01"}]
                }
                """.formatted(skillItem, educationIdField, school);
    }
}
