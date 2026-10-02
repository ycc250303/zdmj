package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.resumeService.enums.AwardTypeEnum;
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
    void 枚举码_合法值写入数据库_响应同时给出整数码和枚举名() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long educationId = ApiClient.dataId(api.post(token, "/educations", """
                {"school":"测试大学","major":"计算机","degree":3,"startDate":"2020-09-01"}
                """));
        long awardId = ApiClient.dataId(api.post(token, "/awards", """
                {"awardType":2,"name":"竞赛奖","awardDate":"2024-05-01"}
                """));

        assertThat(jdbcTemplate.queryForObject("SELECT degree FROM educations WHERE id = ?", Integer.class,
                educationId)).isEqualTo(EducationDegreeEnum.BACHELOR.getCode());
        assertThat(jdbcTemplate.queryForObject("SELECT award_type FROM awards WHERE id = ?", Integer.class, awardId))
                .isEqualTo(AwardTypeEnum.COMPETITION.getCode());

        expectGet(token, "/educations/" + educationId)
                .andExpect(jsonPath("$.data.degree").value(3))
                .andExpect(jsonPath("$.data.degreeEnum").value("BACHELOR"));
        expectGet(token, "/awards/" + awardId)
                .andExpect(jsonPath("$.data.awardType").value(2))
                .andExpect(jsonPath("$.data.awardTypeEnum").doesNotExist());

        jdbcTemplate.update("UPDATE educations SET degree = 99 WHERE id = ?", educationId);
        assertThat(jdbcTemplate.queryForObject("SELECT degree FROM educations WHERE id = ?", Integer.class,
                educationId)).isEqualTo(99);
        expectGet(token, "/educations/" + educationId)
                .andExpect(jsonPath("$.data.degree").value(nullValue()))
                .andExpect(jsonPath("$.data.degreeEnum").value(nullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions expectGet(String token, String path) throws Exception {
        return ApiClient.expectOk(api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(ApiClient.PREFIX + path)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void 枚举码_空值和越界值_拒绝写入且数据库没有新行() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        ApiClient.assertProblem(api.post(token, "/educations", """
                {"school":"测试大学","major":"计算机","startDate":"2020-09-01"}
                """), 400, 1001);
        ApiClient.assertProblem(api.post(token, "/educations", """
                {"school":"测试大学","major":"计算机","degree":9,"startDate":"2020-09-01"}
                """), 400, 1001);
        ApiClient.assertProblem(api.post(token, "/awards", """
                {"name":"竞赛奖","awardDate":"2024-05-01"}
                """), 400, 1001);
        ApiClient.assertProblem(api.post(token, "/awards", """
                {"awardType":9,"name":"竞赛奖","awardDate":"2024-05-01"}
                """), 400, 1001);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM educations", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM awards", Integer.class)).isZero();
    }
}
