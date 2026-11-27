package com.zdmj.integration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.MutableClock;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 验证文档归属、时间和覆盖向量事务，故障仅注入隔离数据库调用。 */
@AutoConfigureMockMvc
class KnowledgeWriteIT extends IntegrationTestBase {
    @Autowired private TestUsers users;
    @Autowired private TestJwt jwt;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionTemplate sessions;
    @Autowired private MutableClock clock;
    @Autowired private KnowledgeEmbeddingService embedding;
    @MockitoSpyBean private KnowledgeVectorMapper vectors;

    @Autowired
    KnowledgeWriteIT(DatabaseCleaner cleaner) { super(cleaner); }

    @Test
    void 上传登记本人私有文档并使用应用时间() throws Exception {
        var user = users.createPair().userA();
        String token = jwt.authorize(user);
        clock.setInstant(Instant.now().plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        var expectedTime = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(clock));
        long document = upload(token, "知识内容");
        assertThat(jdbc.queryForMap("SELECT user_id,scope,created_at,updated_at FROM knowledge_documents WHERE id=?", document))
                .containsEntry("user_id", user.getId()).containsEntry("scope", KnowledgeScopeEnum.USER.getCode())
                .containsEntry("created_at", expectedTime).containsEntry("updated_at", expectedTime);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.knowledge_bases')::text", String.class)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    void 实际插入后返回异常数量_覆盖事务回滚且旧向量和完成信息保留(int count) throws Exception {
        var user = users.createPair().userA();
        long document = upload(jwt.authorize(user), "保留旧向量的知识内容");
        var oldVectors = vectorRows(document);
        var oldInfo = completedInfo(document);
        doAnswer(invocation -> {
            sessions.getMapper(KnowledgeVectorMapper.class).batchInsert(invocation.getArgument(0));
            return count;
        }).when(vectors).batchInsert(anyList());
        assertThatThrownBy(() -> embedding.vectorizeAndStore(new CurrentActor(user.getId()), document))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("向量写入数量");
        assertThat(vectorRows(document)).isEqualTo(oldVectors);
        assertThat(completedInfo(document)).isEqualTo(oldInfo);
        assertThat(jdbc.queryForObject("SELECT embedding_status FROM knowledge_documents WHERE id=?", Integer.class, document)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT file_status FROM knowledge_documents WHERE id=?", Integer.class, document)).isEqualTo(1);
    }

    @Test
    void 向量插入数据库失败_覆盖事务回滚并保留数据库原始原因() throws Exception {
        var user = users.createPair().userA();
        long document = upload(jwt.authorize(user), "数据库错误保留旧向量");
        var old = vectorRows(document);
        doAnswer(invocation -> {
            List<KnowledgeVector> rows = invocation.getArgument(0);
            rows.getFirst().setEmbedding("[1]");
            return sessions.getMapper(KnowledgeVectorMapper.class).batchInsert(rows);
        }).when(vectors).batchInsert(anyList());
        assertThatThrownBy(() -> embedding.vectorizeAndStore(new CurrentActor(user.getId()), document))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThat(vectorRows(document)).isEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT embedding_status FROM knowledge_documents WHERE id=?", Integer.class, document)).isEqualTo(4);
    }

    @Test
    void 向量化完成时间_使用保存阶段的应用时间() throws Exception {
        var user = users.createPair().userA();
        long document = upload(jwt.authorize(user), "应用时间测试");
        Instant completed = Instant.now().plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        doAnswer(invocation -> {
            clock.setInstant(completed);
            return sessions.getMapper(KnowledgeVectorMapper.class).batchInsert(invocation.getArgument(0));
        }).when(vectors).batchInsert(anyList());
        embedding.vectorizeAndStore(new CurrentActor(user.getId()), document);
        var time = java.sql.Timestamp.valueOf(java.time.LocalDateTime.ofInstant(completed, clock.getZone()));
        assertThat(jdbc.queryForMap("SELECT last_embedded_at,updated_at FROM knowledge_documents WHERE id=?", document))
                .containsEntry("last_embedded_at", time).containsEntry("updated_at", time);
    }

    private long upload(String token, String text) throws Exception {
        return ApiClient.dataId(mvc.perform(multipart(ApiClient.PREFIX + "/knowledge-document")
                .file(new MockMultipartFile("file", "test.md", "text/markdown", text.getBytes(StandardCharsets.UTF_8)))
                .param("title", "文档").param("type", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn());
    }

    private List<Map<String,Object>> vectorRows(long document) {
        return jdbc.queryForList("SELECT id,content,embedding::text FROM knowledge_vectors WHERE document_id=? ORDER BY id", document);
    }

    private Map<String,Object> completedInfo(long document) {
        return jdbc.queryForMap("SELECT chunk_count,last_embedded_at FROM knowledge_documents WHERE id=?", document);
    }
}
