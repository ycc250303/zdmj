package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.resumeService.enums.EducationDegreeEnum;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class EnumCodePersistenceIT extends IntegrationTestBase {

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EnumCodePersistenceIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 枚举码_合法值经整份保存写入_读取仍返回原整数码() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        String saved = ApiClient.requireOk(api.put(token, "/resumes/me/content", content(3, "竞赛奖")));
        long educationId = JsonPath.<Number>read(saved, "$.data.educations[0].id").longValue();

        assertThat(jdbcTemplate.queryForObject("SELECT degree FROM educations WHERE id = ?", Integer.class,
                educationId)).isEqualTo(EducationDegreeEnum.BACHELOR.getCode());
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM awards WHERE user_id IS NOT NULL AND name = '竞赛奖'",
                String.class)).isEqualTo("竞赛奖");

        String loaded = ApiClient.requireOk(api.get(token, "/resumes/me/content"));
        assertThat(JsonPath.<Integer>read(loaded, "$.data.educations[0].degree")).isEqualTo(3);
        assertThat(JsonPath.<String>read(loaded, "$.data.awards[0].name")).isEqualTo("竞赛奖");
    }

    @Test
    void 枚举码_空值和越界值_拒绝整份保存且数据库没有新行() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        ApiClient.assertProblem(api.put(token, "/resumes/me/content", content(null, "竞赛奖")), 400, 1001);
        ApiClient.assertProblem(api.put(token, "/resumes/me/content", content(9, "竞赛奖")), 400, 1001);
        ApiClient.assertProblem(api.put(token, "/resumes/me/content", content(3, null)), 400, 1001);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM educations", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM awards", Integer.class)).isZero();
    }

    private static String content(Integer degree, String awardName) {
        String degreeField = degree == null ? "" : "\"degree\": " + degree + ", ";
        String award = awardName == null
                ? "{\"awardDate\": \"2024-05-01\"}"
                : "{\"name\": \"" + awardName + "\", \"awardDate\": \"2024-05-01\"}";
        return """
                {
                  "skills": [{"name": "Java"}],
                  "educations": [{"school": "测试大学", "major": "计算机", %s"startDate": "2020-09-01"}],
                  "careers": [],
                  "projects": [],
                  "awards": [%s]
                }
                """.formatted(degreeField, award);
    }
}
