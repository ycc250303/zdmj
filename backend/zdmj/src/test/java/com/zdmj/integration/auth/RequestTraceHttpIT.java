package com.zdmj.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/** 通过实际 HTTP、完整过滤器链和隔离数据库验证请求日志关联。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.slf4j.Slf4jImpl")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RequestTraceHttpIT extends IntegrationTestBase {

    @Autowired private TestRestTemplate http;

    @Autowired
    RequestTraceHttpIT(DatabaseCleaner cleaner) {
        super(cleaner);
    }

    @Test
    void 岗位查询_响应编号贯穿实际SQL和请求日志_忽略客户端编号(CapturedOutput output) {
        var headers = new HttpHeaders();
        headers.set("X-Trace-Id", "client-supplied-trace");
        ResponseEntity<String> response = http.exchange("/api/zdmj/jobs?page=1&limit=2",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(response.getBody(), "$.code")).isZero();
        String traceId = requireTraceId(response);
        List<String> logs = output.getAll().lines()
                .filter(line -> line.contains("[traceId=" + traceId + "]")).toList();
        assertThat(logs).anyMatch(line -> line.contains("HTTP 请求开始 method=GET path=/api/zdmj/jobs"));
        assertThat(logs).anyMatch(line -> line.contains("Preparing:") && line.contains("jobs"));
        assertThat(logs).anyMatch(line -> line.contains("HTTP 请求结束") && line.contains("status=200"));
        assertThat(logs).noneMatch(line -> line.contains("client-supplied-trace"));
    }

    @Test
    void 鉴权拒绝与业务错误和JSON错误_正文与响应头编号一致(CapturedOutput output) {
        ResponseEntity<String> unauthorized = http.getForEntity("/api/zdmj/users/1", String.class);
        assertProblem(unauthorized, 401);
        assertThat(JsonPath.<Integer>read(unauthorized.getBody(), "$.code")).isEqualTo(1002);

        ResponseEntity<String> missing = http.getForEntity("/api/zdmj/jobs/999999", String.class);
        assertProblem(missing, 404);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> malformed = http.postForEntity("/api/zdmj/users/login",
                new HttpEntity<>("{", headers), String.class);
        assertProblem(malformed, 400);

        for (var response : List.of(unauthorized, missing, malformed)) {
            assertThat(output.getAll().lines()
                    .filter(line -> line.contains("[traceId=" + requireTraceId(response) + "]")))
                    .anyMatch(line -> line.contains("HTTP 请求结束")
                            && line.contains("status=" + response.getStatusCode().value()));
        }
    }

    @Test
    void 跨域请求_浏览器可以读取关联编号() {
        var headers = new HttpHeaders();
        headers.setOrigin("http://localhost:9527");
        ResponseEntity<String> response = http.exchange("/api/zdmj/jobs", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        requireTraceId(response);
        assertThat(response.getHeaders().getAccessControlExposeHeaders()).contains("X-Trace-Id");
    }

    @Test
    void 并发HTTP请求_每个响应使用独立编号且日志没有串用(CapturedOutput output) throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> http.getForEntity(
                            "/api/zdmj/jobs/" + (900000 + index), String.class)))
                    .toList();
            var traceIds = new java.util.HashSet<String>();
            for (int index = 0; index < tasks.size(); index++) {
                var response = tasks.get(index).get(20, TimeUnit.SECONDS);
                assertProblem(response, 404);
                String traceId = requireTraceId(response);
                assertThat(traceIds.add(traceId)).isTrue();
                String path = "path=/api/zdmj/jobs/" + (900000 + index);
                assertThat(output.getAll().lines()
                        .filter(line -> line.contains("[traceId=" + traceId + "]")
                                && line.contains("HTTP 请求")))
                        .hasSize(2).allMatch(line -> line.contains(path));
            }
        }
    }

    private void assertProblem(ResponseEntity<String> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(JsonPath.<String>read(response.getBody(), "$.traceId")).isEqualTo(requireTraceId(response));
    }

    private String requireTraceId(ResponseEntity<String> response) {
        String traceId = response.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).matches("[0-9a-f]{32}");
        return traceId;
    }
}
