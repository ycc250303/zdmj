package com.zdmj.integration.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

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

@AutoConfigureMockMvc
class JobQueryFlowIT extends IntegrationTestBase {

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    JobQueryFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 岗位查询_分页筛选与更新时间倒序_每页无重复且详情字段一致() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long newest = createJob(token, "后端工程师", "星云科技", "互联网", 2, 20000, 30000);
        long middle = createJob(token, "测试工程师", "青禾数据", "企业服务", 2, 15000, 25000);
        long oldest = createJob(token, "实习生", "北海制造", "制造业", 1, 200, 400);
        stamp(newest, LocalDateTime.of(2024, 9, 3, 10, 0));
        stamp(middle, LocalDateTime.of(2024, 9, 2, 10, 0));
        stamp(oldest, LocalDateTime.of(2024, 9, 1, 10, 0));

        String page1 = ApiClient.requireOk(api.get(token, "/jobs?page=1&limit=2"));
        String page2 = ApiClient.requireOk(api.get(token, "/jobs?page=2&limit=2"));
        assertThat(JsonPath.<Integer>read(page1, "$.data.total")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(page1, "$.data.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(page1, "$.data.limit")).isEqualTo(2);
        List<Integer> firstIds = JsonPath.read(page1, "$.data.list[*].id");
        List<Integer> secondIds = JsonPath.read(page2, "$.data.list[*].id");
        assertThat(firstIds).containsExactly((int) newest, (int) middle);
        assertThat(secondIds).containsExactly((int) oldest);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);

        String repeated = ApiClient.requireOk(api.get(token, "/jobs?page=1&limit=2"));
        assertThat(JsonPath.<List<Integer>>read(repeated, "$.data.list[*].id")).containsExactlyElementsOf(firstIds);

        String byName = ApiClient.requireOk(api.get(token, "/jobs?jobName=后端&limit=10"));
        assertThat(JsonPath.<List<String>>read(byName, "$.data.list[*].jobName")).containsExactly("后端工程师");

        String byCompany = ApiClient.requireOk(api.get(token, "/jobs?companyName=星云&limit=10"));
        assertThat(JsonPath.<Integer>read(byCompany, "$.data.total")).isEqualTo(1);

        String byIndustry = ApiClient.requireOk(api.get(token, "/jobs?industries=[\"互联网\"]&limit=10"));
        assertThat(JsonPath.<List<String>>read(byIndustry, "$.data.list[*].jobName")).containsExactly("后端工程师");

        String bySalary = ApiClient.requireOk(api.get(token,
                "/jobs?salaryType=2&filterSalaryMin=18000&filterSalaryMax=32000&limit=10"));
        assertThat(JsonPath.<List<String>>read(bySalary, "$.data.list[*].jobName")).containsExactly("后端工程师");

        String detail = ApiClient.requireOk(api.get(token, "/jobs/" + newest));
        assertThat(JsonPath.<String>read(detail, "$.data.jobName")).isEqualTo("后端工程师");
        assertThat(JsonPath.<String>read(detail, "$.data.companyName")).isEqualTo("星云科技");
        assertThat(JsonPath.<String>read(detail, "$.data.location")).isEqualTo("上海");
        assertThat(JsonPath.<Integer>read(detail, "$.data.salaryMin")).isEqualTo(20000);
    }

    private long createJob(String token, String jobName, String companyName, String industry, int salaryType,
            int salaryMin, int salaryMax) throws Exception {
        String json = """
                {
                  "jobName": "%s",
                  "companyName": "%s",
                  "companyIndustries": ["%s"],
                  "description": "负责%s相关工作",
                  "location": "上海",
                  "salaryMin": %d,
                  "salaryMax": %d,
                  "salaryType": %d,
                  "keywords": ["Java"]
                }
                """.formatted(jobName, companyName, industry, jobName, salaryMin, salaryMax, salaryType);
        return ApiClient.dataId(api.post(token, "/jobs", json));
    }

    private void stamp(long jobId, LocalDateTime updatedAt) {
        int rows = jdbcTemplate.update("UPDATE jobs SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(updatedAt), jobId);
        if (rows != 1) {
            throw new IllegalStateException("更新岗位时间失败: " + jobId);
        }
    }
}
