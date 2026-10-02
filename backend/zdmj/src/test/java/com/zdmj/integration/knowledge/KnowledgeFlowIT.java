package com.zdmj.integration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

@AutoConfigureMockMvc
class KnowledgeFlowIT extends IntegrationTestBase {

    private final ApiClient api;
    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Autowired
    KnowledgeFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate, KnowledgeEmbeddingService knowledgeEmbeddingService) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
    }

    @Test
    void 知识库检索_创建文档并写入固定向量_归属正确且范围过滤只命中指定文档() throws Exception {
        User user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);

        String base = ApiClient.requireOk(api.post(token, "/knowledge", null));
        long knowledgeId = JsonPath.<Number>read(base, "$.data.id").longValue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM knowledge_bases WHERE id = ?", Long.class, knowledgeId)).isEqualTo(user.getId());

        String alphaUrl = upload(token, "alpha.md", "固定知识片段甲");
        String betaUrl = upload(token, "beta.md", "固定知识片段乙");
        long alphaId = createDocument(token, "片段甲", alphaUrl);
        long betaId = createDocument(token, "片段乙", betaUrl);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM knowledge_documents WHERE id = ?", Long.class, alphaId)).isEqualTo(user.getId());

        knowledgeEmbeddingService.vectorizeAndStore(alphaId);
        knowledgeEmbeddingService.vectorizeAndStore(betaId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM knowledge_vectors WHERE document_id = ? AND user_id = ?",
                Integer.class, alphaId, user.getId())).isPositive();

        String scoped = ApiClient.requireOk(api.post(token, "/knowledge/retrievals", """
                {"query":"检索甲","ragDocumentIds":[%d],"useSystemKnowledge":false}
                """.formatted(alphaId)));
        List<Integer> hitIds = JsonPath.read(scoped, "$.data.hits[*].documentId");
        assertThat(hitIds).isNotEmpty();
        assertThat(hitIds).allMatch(id -> id.longValue() == alphaId);
        assertThat(JsonPath.<String>read(scoped, "$.data.hits[0].content")).contains("固定知识片段甲");

        String closed = ApiClient.requireOk(api.post(token, "/knowledge/retrievals", """
                {"query":"检索甲","ragDocumentIds":[],"useSystemKnowledge":false}
                """));
        assertThat(JsonPath.<Integer>read(closed, "$.data.hits.length()")).isZero();
        assertThat(betaId).isNotEqualTo(alphaId);
    }

    private String upload(String token, String fileName, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/markdown",
                text.getBytes(StandardCharsets.UTF_8));
        var result = mockMvc.perform(multipart(ApiClient.PREFIX + "/files/upload")
                        .file(file)
                        .param("prefix", "knowledge")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        return JsonPath.read(ApiClient.requireOk(result), "$.data.url");
    }

    private long createDocument(String token, String title, String url) throws Exception {
        String json = """
                {"title":"%s","type":1,"content":"%s"}
                """.formatted(title, url);
        return ApiClient.dataId(api.post(token, "/knowledge-document", json));
    }
}
