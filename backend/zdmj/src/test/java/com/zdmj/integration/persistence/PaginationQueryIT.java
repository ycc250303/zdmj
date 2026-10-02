package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class PaginationQueryIT extends IntegrationTestBase {

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;

    @Autowired
    PaginationQueryIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
    }

    @Test
    void 岗位分页_默认值零页和超上限_规范页码并截断limit() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        createJob(token, "岗位一");

        String defaults = ApiClient.requireOk(api.get(token, "/jobs"));
        assertThat(JsonPath.<Integer>read(defaults, "$.data.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(defaults, "$.data.limit")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(defaults, "$.data.total")).isEqualTo(1);

        String normalized = ApiClient.requireOk(api.get(token, "/jobs?page=0&limit=2"));
        String firstPage = ApiClient.requireOk(api.get(token, "/jobs?page=1&limit=2"));
        assertThat(JsonPath.<Integer>read(normalized, "$.data.page")).isEqualTo(1);
        assertThat(JsonPath.<List<Integer>>read(normalized, "$.data.list[*].id"))
                .containsExactlyElementsOf(JsonPath.read(firstPage, "$.data.list[*].id"));

        String capped = ApiClient.requireOk(api.get(token, "/jobs?limit=500"));
        assertThat(JsonPath.<Integer>read(capped, "$.data.limit")).isEqualTo(100);
        assertThat(JsonPath.<Integer>read(capped, "$.data.total")).isEqualTo(1);

        ApiClient.assertProblem(api.get(token, "/jobs?page=abc"), 400, 1001);
    }

    private long createJob(String token, String jobName) throws Exception {
        String json = """
                {
                  "jobName": "%s",
                  "companyName": "%s公司",
                  "companyIndustries": ["互联网"],
                  "description": "负责%s",
                  "location": "上海",
                  "salaryMin": 20000,
                  "salaryMax": 30000,
                  "salaryType": 2,
                  "keywords": ["Java"]
                }
                """.formatted(jobName, jobName, jobName);
        return ApiClient.dataId(api.post(token, "/jobs", json));
    }
}
