package com.zdmj.integration.knowledge;

import com.zdmj.common.context.CurrentActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.zdmj.testsupport.TestObjectStorage;
import org.springframework.test.web.servlet.MvcResult;

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

        knowledgeEmbeddingService.vectorizeAndStore(new CurrentActor(user.getId()), alphaId);
        knowledgeEmbeddingService.vectorizeAndStore(new CurrentActor(user.getId()), betaId);
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

    @Test
    void 跨用户向量化与删除_拒绝访问且保留原文档和向量() throws Exception {
        TestUsers.Pair pair = testUsers.createPair();
        String token = testJwt.authorize(pair.userA());
        String url = upload(token, "private.md", "个人文档内容");
        long documentId = createDocument(token, "个人文档", url);
        var beforeDocument = jdbcTemplate.queryForMap("SELECT * FROM knowledge_documents WHERE id = ?", documentId);
        var beforeVectors = jdbcTemplate.queryForList(
                "SELECT * FROM knowledge_vectors WHERE document_id = ? ORDER BY id", documentId);
        assertThat(beforeVectors).isNotEmpty();
        CurrentActor otherActor = new CurrentActor(pair.userB().getId());

        assertThatThrownBy(() -> knowledgeEmbeddingService.vectorizeAndStore(otherActor, documentId))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode());
        assertThatThrownBy(() -> knowledgeEmbeddingService.deleteVectors(otherActor, documentId))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode());

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM knowledge_documents WHERE id = ?", documentId))
                .isEqualTo(beforeDocument);
        assertThat(jdbcTemplate.queryForList(
                "SELECT * FROM knowledge_vectors WHERE document_id = ? ORDER BY id", documentId))
                .isEqualTo(beforeVectors);
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

    @Test
    void 上传分类与默认目录_保持响应字段并按当前用户列出() throws Exception {
        var pair = testUsers.createPair();
        String token = testJwt.authorize(pair.userA());
        for (String category : new String[]{"files", "resume", "profile", "knowledge"}) {
            String body = ApiClient.requireOk(upload(token, category));
            assertThat(JsonPath.<String>read(body, "$.data.key"))
                    .startsWith("user-" + pair.userA().getId() + "/" + category + "/");
            assertThat(JsonPath.<String>read(body, "$.data.fileName")).isEqualTo("简历.md");
            assertThat(JsonPath.<Integer>read(body, "$.data.fileSize")).isEqualTo(4);
            assertThat(JsonPath.<String>read(body, "$.data.contentType")).isEqualTo("text/markdown");
            assertThat(JsonPath.<String>read(body, "$.data.url")).startsWith(TestObjectStorage.URL_PREFIX);
        }
        String defaultUpload = ApiClient.requireOk(upload(token, null));
        assertThat(JsonPath.<String>read(defaultUpload, "$.data.key")).contains("/files/");
        assertThat(JsonPath.<Integer>read(ApiClient.requireOk(api.get(token, "/files/list")), "$.data.length()"))
                .isEqualTo(5);
        assertThat(JsonPath.<Integer>read(ApiClient.requireOk(api.get(token, "/files/list?prefix=resume")), "$.data.length()"))
                .isEqualTo(1);
        String otherToken = testJwt.authorize(pair.userB());
        assertThat(JsonPath.<Integer>read(ApiClient.requireOk(api.get(otherToken, "/files/list")), "$.data.length()"))
                .isZero();
    }

    @Test
    void 非法分类_上传与列表均拒绝且没有写入对象() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        for (String category : new String[]{"../resume", "user-2", "avatar", "RESUME"}) {
            ApiClient.assertProblem(upload(token, category), 400, 1001);
            ApiClient.assertProblem(api.exchange(get(ApiClient.PREFIX + "/files/list").param("prefix", category), token, null),
                    400, 1001);
        }
        assertThat(JsonPath.<Integer>read(ApiClient.requireOk(api.get(token, "/files/list")), "$.data.length()"))
                .isZero();
    }

    @Test
    void 非托管与跨用户文档来源_创建拒绝且不产生文档() throws Exception {
        var pair = testUsers.createPair();
        String tokenA = testJwt.authorize(pair.userA());
        String tokenB = testJwt.authorize(pair.userB());
        String ownUrl = JsonPath.read(ApiClient.requireOk(upload(tokenA, "knowledge")), "$.data.url");
        String foreignUrl = "https://evil.example/" + ownUrl.substring(TestObjectStorage.URL_PREFIX.length());
        ApiClient.assertProblem(api.post(tokenA, "/knowledge-document", document(foreignUrl)), 400, 1007);
        ApiClient.assertProblem(api.post(tokenB, "/knowledge-document", document(ownUrl)), 403, 1003);
        assertThat(JsonPath.<Integer>read(ApiClient.requireOk(api.get(tokenA, "/knowledge-document")), "$.data.total"))
                .isZero();
    }

    private MvcResult upload(String token, String category) throws Exception {
        var request = multipart(ApiClient.PREFIX + "/files/upload")
                .file(new MockMultipartFile("file", "简历.md", "text/markdown", "text".getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (category != null) {
            request.param("prefix", category);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String document(String url) {
        return "{\"title\":\"文档\",\"type\":1,\"content\":\"" + url + "\"}";
    }
}
