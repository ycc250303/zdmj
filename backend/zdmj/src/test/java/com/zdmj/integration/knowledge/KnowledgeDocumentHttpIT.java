package com.zdmj.integration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.knowledgeService.config.KnowledgeDocumentConfig;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestUsers;

/** 使用随机端口和真实 HTTP 验证登录、知识文档上传、持久化、下载和重新处理。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(120)
class KnowledgeDocumentHttpIT extends IntegrationTestBase {

    @Autowired private TestRestTemplate http;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KnowledgeDocumentConfig documentConfig;

    @Autowired
    KnowledgeDocumentHttpIT(DatabaseCleaner databaseCleaner) {
        super(databaseCleaner);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 长文档通过真实上传请求完整向量化并可下载和重新处理(boolean pdf) throws Exception {
        String token = login();
        String text = pdf
                ? "Knowledge document contains job requirements and project experience.\n".repeat(2_000) + "DOCUMENT_END_MARKER"
                : "知识文档包含岗位要求和项目经验。\n".repeat(8_000) + "DOCUMENT_END_MARKER";
        byte[] bytes = pdf ? pdf(text) : text.getBytes(StandardCharsets.UTF_8);
        assertThat(text.length()).isGreaterThan(100_000);

        String result = requireOk(upload(token, pdf ? "long.pdf" : "long.md", bytes));
        long id = JsonPath.<Number>read(result, "$.data.id").longValue();
        assertThat(JsonPath.<Integer>read(result, "$.data.embeddingStatus")).isEqualTo(3);
        List<String> chunks = assertCompleteVectors(id, text);
        assertDownload(token, id, bytes);

        requireOk(http.exchange(ApiClient.PREFIX + "/knowledge-document/" + id + "/embedding",
                HttpMethod.POST, new HttpEntity<>(headers(token)), String.class));
        assertThat(assertCompleteVectors(id, text)).isEqualTo(chunks);
    }

    @Test
    void 接近一百万字符的文档通过真实上传请求完整处理() {
        String token = login();
        String text = "document content ".repeat(58_000) + "DOCUMENT_END_MARKER";
        assertThat(text.length()).isBetween(980_000, documentConfig.maxExtractedChars());

        String result = requireOk(upload(token, "capacity.md", text.getBytes(StandardCharsets.UTF_8)));

        long id = JsonPath.<Number>read(result, "$.data.id").longValue();
        assertCompleteVectors(id, text);
    }

    @Test
    void 超限请求返回失败详情且保留完整原文件() {
        String token = login();
        byte[] bytes = "A".repeat(documentConfig.maxExtractedChars() + 1).getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> response = upload(token, "over-limit.md", bytes);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(JsonPath.<Integer>read(response.getBody(), "$.code")).isEqualTo(1001);
        assertThat(JsonPath.<String>read(response.getBody(), "$.detail"))
                .contains(Integer.toString(documentConfig.maxExtractedChars()));
        long id = JsonPath.<Number>read(response.getBody(), "$.documentId").longValue();
        assertThat(jdbc.queryForObject("SELECT embedding_status FROM knowledge_documents WHERE id=?", Integer.class, id))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_vectors WHERE document_id=?", Integer.class, id))
                .isZero();
        assertDownload(token, id, bytes);
    }

    private String login() {
        var user = users.createPair().userA();
        ResponseEntity<String> response = http.postForEntity(ApiClient.PREFIX + "/users/login",
                Map.of("usernameOrEmail", user.getUsername(), "password", TestUsers.PASSWORD), String.class);
        return JsonPath.read(requireOk(response), "$.data.token");
    }

    private ResponseEntity<String> upload(String token, String fileName, byte[] bytes) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("title", "长知识文档");
        form.add("type", "1");
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity(ApiClient.PREFIX + "/knowledge-document",
                new HttpEntity<>(form, headers), String.class);
    }

    private List<String> assertCompleteVectors(long id, String expected) {
        List<String> chunks = jdbc.queryForList(
                "SELECT content FROM knowledge_vectors WHERE document_id=? ORDER BY chunk_index", String.class, id);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join("", chunks).replaceAll("\\s+", "")).isEqualTo(expected.replaceAll("\\s+", ""));
        assertThat(chunks.getLast()).endsWith("DOCUMENT_END_MARKER");
        assertThat(jdbc.queryForObject("SELECT chunk_count FROM knowledge_documents WHERE id=?", Integer.class, id))
                .isEqualTo(chunks.size());
        return chunks;
    }

    private void assertDownload(String token, long id, byte[] expected) {
        ResponseEntity<byte[]> response = http.exchange(ApiClient.PREFIX + "/knowledge-document/" + id + "/download",
                HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(expected.length);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static String requireOk(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).as("HTTP 响应：%s", response.getBody()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(response.getBody(), "$.code")).isZero();
        return response.getBody();
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            String[] lines = text.split("\n");
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int start = 0; start < lines.length; start += 45) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 10);
                    content.setLeading(14);
                    content.newLineAtOffset(40, 750);
                    for (int index = start; index < Math.min(start + 45, lines.length); index++) {
                        content.showText(lines[index]);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
