package com.zdmj.integration.resume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

@AutoConfigureMockMvc
class ResumeFlowIT extends IntegrationTestBase {

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ResumeFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 简历全流程_保存后按用户读取经历和技能() throws Exception {
        User user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);

        String saved = ApiClient.requireOk(api.put(token, "/resumes/me/content", contentJson(null, "Java", "测试大学")));
        assertThat(JsonPath.<String>read(saved, "$.data.skill.content[0].type")).isEqualTo("编程语言");
        assertThat(JsonPath.<String>read(saved, "$.data.skill.content[0].content[0]")).isEqualTo("Java");
        assertThat(JsonPath.<String>read(saved, "$.data.educations[0].school")).isEqualTo("测试大学");
        assertThat(JsonPath.<String>read(saved, "$.data.educations[0].startDate")).isEqualTo("2020-09-01");
        assertThat(JsonPath.<Integer>read(saved, "$.data.educations[0].degree")).isEqualTo(3);
        assertThat(JsonPath.<String>read(saved, "$.data.careers[0].company")).isEqualTo("测试公司");
        assertThat(JsonPath.<String>read(saved, "$.data.projects[0].name")).isEqualTo("测试项目");
        assertThat(JsonPath.<String>read(saved, "$.data.awards[0].name")).isEqualTo("测试奖项");

        String loaded = ApiClient.requireOk(api.get(token, "/resumes/me/content"));
        assertThat(JsonPath.<String>read(loaded, "$.data.educations[0].school")).isEqualTo("测试大学");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM educations WHERE user_id = ? AND school = '测试大学'", Integer.class, user.getId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM skills WHERE user_id = ?", Integer.class, user.getId())).isEqualTo(1);
    }

    private static String contentJson(Long educationId, String skillItem, String school) {
        String educationIdField = educationId == null ? "" : "\"id\":" + educationId + ",";
        return """
                {
                  "skill": {"content": [{"type": "编程语言", "content": ["%s"]}]},
                  "educations": [{%s"school": "%s", "major": "计算机", "degree": 3, "startDate": "2020-09-01", "endDate": "2024-06-30", "gpa": "3.8"}],
                  "careers": [{"company": "测试公司", "position": "后端实习生", "startDate": "2023-07-01"}],
                  "projects": [{"name": "测试项目", "startDate": "2023-01-01", "role": "开发", "description": "实现接口", "contribution": "完成后端"}],
                  "awards": [{"name": "测试奖项", "awardDate": "2024-05-01"}]
                }
                """.formatted(skillItem, educationIdField, school);
    }
}
