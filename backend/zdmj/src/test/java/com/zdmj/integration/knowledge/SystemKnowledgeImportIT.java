package com.zdmj.integration.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.knowledgeService.service.impl.SystemKnowledgeImportServiceImpl;
import org.springframework.context.annotation.Import;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportRequest;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.SystemKnowledgeImportService;
import com.zdmj.testsupport.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 隔离数据库验证整批导入与用户上传的一致性，模型使用固定1024维替身。 */
@Import({SystemKnowledgeImportServiceImpl.class, SystemKnowledgeImportIT.ImportConfig.class})
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemKnowledgeImportIT extends IntegrationTestBase {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(com.zdmj.knowledgeService.config.SystemKnowledgeImportConfig.class)
    static class ImportConfig { }
    private static final Path ROOT = createRoot();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestUsers users;
    @Autowired TestJwt jwt;
    @Autowired SqlSessionTemplate sessions;
    @Autowired SystemKnowledgeImportService service;
    @MockitoSpyBean KnowledgeVectorMapper vectors;
    @MockitoSpyBean EmbeddingGateway gateway;
    @MockitoSpyBean org.springframework.ai.transformer.splitter.TextSplitter splitter;

    @Autowired SystemKnowledgeImportIT(DatabaseCleaner cleaner) { super(cleaner); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("knowledge.system-import.root", ROOT::toString);
    }

    @Test
    void 仅新增和正文变更文档调用模型且未变化文档保留全部片段() throws Exception {
        seedOtherSources();
        String stable = "Java内存模型与线程安全知识";
        service.importDocuments(fixture(Map.of("java/a.md", "旧版Java并发资料。".repeat(600),
                "java/b.md", stable, "java/removed.md", "将被移除的资料")));
        long a = documentId("java/a.md"), b = documentId("java/b.md"), removed = documentId("java/removed.md");
        var oldA = chunkIds(a);
        assertThat(oldA).hasSizeGreaterThan(1);
        var oldB = vectorSnapshot(b);
        var embeddedAt = jdbc.queryForObject("SELECT last_embedded_at FROM knowledge_documents WHERE id=?", java.time.LocalDateTime.class, b);
        clearInvocations(gateway, vectors);
        String changed = "新版Java并发机制。".repeat(650), added = "新增的数据库事务资料";
        var next = fixture(Map.of("java/a.md", changed, "java/b.md", stable, "java/c.md", added), "d".repeat(40), "");
        var result = service.importDocuments(next);
        assertThat(result.documents()).isEqualTo(3);
        assertThat(result.chunks()).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM knowledge_vectors", Integer.class));
        assertThat(documentId("java/a.md")).isEqualTo(a);
        assertThat(chunkIds(a)).isNotEmpty().doesNotContainAnyElementsOf(oldA);
        assertThat(jdbc.queryForList("SELECT id FROM knowledge_vectors", Long.class)).doesNotContainAnyElementsOf(oldA);
        assertThat(vectorSnapshot(b)).isEqualTo(oldB);
        assertThat(jdbc.queryForObject("SELECT last_embedded_at FROM knowledge_documents WHERE id=?", java.time.LocalDateTime.class, b)).isEqualTo(embeddedAt);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_documents WHERE id=?", Integer.class, removed)).isZero();
        assertThat(chunkIds(removed)).isEmpty();
        assertThat(chunkIds(documentId("java/c.md"))).isNotEmpty();
        var inputs = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(gateway, atLeastOnce()).embedSystem(inputs.capture());
        assertThat(inputs.getAllValues().stream().flatMap(List::stream).toList()).contains(added).doesNotContain(stable);
        verify(vectors, never()).deleteSystemByDocumentId(b);
        verify(vectors).deleteSystemByDocumentId(a);
        verify(vectors).deleteSystemByDocumentId(removed);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_documents WHERE content IN ('other:system','other:learning','private-source')", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_vectors kv LEFT JOIN knowledge_documents kd ON kd.id=kv.document_id WHERE kd.id IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT scope,user_id,type,file_status,embedding_status FROM knowledge_documents WHERE id=?", a))
                .containsEntry("scope", 2).containsEntry("user_id", null).containsEntry("type", 2)
                .containsEntry("file_status", 1).containsEntry("embedding_status", 3);
    }

    @Test
    void 新运行仅提交来源和标题变化时保留片段且不调用模型() throws Exception {
        var texts = Map.of("java/a.md", "Java技术正文", "java/b.md", "数据库技术正文");
        service.importDocuments(fixture(texts));
        long a = documentId("java/a.md");
        var before = vectorSnapshot(a);
        clearInvocations(gateway, vectors);
        var next = fixture(texts, "d".repeat(40), "更新标题");
        service.importDocuments(next);
        service.importDocuments(next);
        verify(gateway, never()).embedSystem(any());
        verify(vectors, never()).deleteSystemByDocumentId(any());
        verify(vectors, never()).batchInsert(any());
        assertThat(vectorSnapshot(a)).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT title,metadata->>'sourceCommit' AS commit,metadata->>'importRunId' AS run FROM knowledge_documents WHERE id=?", a))
                .containsEntry("title", "java/a.md更新标题").containsEntry("commit", "d".repeat(40)).containsEntry("run", next.runId());
    }

    @Test
    void 两种入口的模型输入分块摘要和顺序完全一致() throws Exception {
        String text = "# Java\n\n" + "Java并发与数据库事务知识。 ".repeat(700) + "短尾部";
        var request = fixture(Map.of("java/test.md", text));
        List<List<String>> systemInputs = new ArrayList<>(), userInputs = new ArrayList<>();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            systemInputs.add(List.copyOf(call.getArgument(0)));
            return call.callRealMethod();
        }).when(gateway).embedSystem(anyList());
        doAnswer(call -> { userInputs.add(List.copyOf(call.getArgument(1))); return call.callRealMethod(); })
                .when(gateway).embed(any(), anyList());
        service.importDocuments(request);
        var user = users.createPair().userA();
        mvc.perform(multipart(ApiClient.PREFIX + "/knowledge-document")
                .file(new MockMultipartFile("file", "test.md", "text/markdown", text.getBytes(StandardCharsets.UTF_8)))
                .param("title", "私有资料").param("type", "1").param("scope", "2")
                .header("Authorization", "Bearer " + jwt.authorize(user))).andExpect(status().isOk());
        assertThat(userInputs).isEqualTo(systemInputs).isNotEmpty();
        assertThat(userInputs.stream().allMatch(batch -> batch.size() <= 10)).isTrue();
        assertThat(jdbc.queryForList("SELECT scope FROM knowledge_documents WHERE user_id=?", Integer.class, user.getId()))
                .containsExactly(1);
        assertThat(jdbc.queryForList("SELECT chunk_index,chunk_hash,token_count,content FROM knowledge_vectors WHERE document_id=(SELECT id FROM knowledge_documents WHERE user_id=?) ORDER BY chunk_index", user.getId()))
                .isEqualTo(jdbc.queryForList("SELECT chunk_index,chunk_hash,token_count,content FROM knowledge_vectors WHERE document_id=(SELECT id FROM knowledge_documents WHERE content='javaguide:docs/java/test.md') ORDER BY chunk_index"));
    }

    @Test
    void 第二篇文档第二批片段写入失败回滚全部文档和片段() throws Exception {
        var old = fixture(Map.of("java/a.md", "# 旧内容\n原始资料", "java/stable.md", "保持不变的正文", "java/removed.md", "将被移除的正文"));
        service.importDocuments(old);
        var before = snapshot();
        var next = fixture(Map.of("java/a.md", "# 新内容\n修改原始资料", "java/b.md", "# 新增\n新文档", "java/stable.md", "保持不变的正文"));
        var chunks = java.util.stream.IntStream.range(0, 501)
                .mapToObj(index -> new org.springframework.ai.document.Document("新片段" + index)).toList();
        doReturn(chunks).when(splitter).apply(anyList());
        List<Integer> sizes = new ArrayList<>();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            List<com.zdmj.knowledgeService.entity.KnowledgeVector> rows = call.getArgument(0);
            sizes.add(rows.size());
            int inserted = sessions.getMapper(KnowledgeVectorMapper.class).batchInsert(rows);
            return sizes.size() == 4 ? 0 : inserted;
        }).when(vectors).batchInsert(anyList());
        assertThatThrownBy(() -> service.importDocuments(next))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("向量写入数量");
        assertThat(sizes).containsExactly(500, 1, 500, 1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 内容摘要错误在模型调用前拒绝且模型失败保留旧资料() throws Exception {
        var old = fixture(Map.of("java/a.md", "原始知识资料"));
        service.importDocuments(old);
        var before = snapshot();
        var next = fixture(Map.of("java/a.md", "更新后的知识资料"));
        Files.writeString(ROOT.resolve("current/docs/java/a.md"), "被修改");
        clearInvocations(gateway);
        var tampered = next;
        assertThatThrownBy(() -> service.importDocuments(tampered)).hasMessageContaining("摘要不一致");
        verify(gateway, never()).embedSystem(any());
        next = fixture(Map.of("java/a.md", "正常的新资料"));
        doThrow(new IllegalStateException("模型异常")).when(gateway).embedSystem(any());
        var valid = next;
        assertThatThrownBy(() -> service.importDocuments(valid)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);
        clearInvocations(gateway);
        doCallRealMethod().when(gateway).embedSystem(any());
        service.importDocuments(valid);
        verify(gateway).embedSystem(any());
        assertThat(jdbc.queryForObject("SELECT content_hash FROM knowledge_documents WHERE id=?", String.class, documentId("java/a.md")))
                .isEqualTo(hash("正常的新资料".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 仅移除文档时删除其片段且其余文档不调用模型() throws Exception {
        service.importDocuments(fixture(Map.of("java/a.md", "保留正文", "java/b.md", "移除正文")));
        long a = documentId("java/a.md"), b = documentId("java/b.md");
        var before = vectorSnapshot(a);
        clearInvocations(gateway, vectors);
        var next = fixture(Map.of("java/a.md", "保留正文"));
        var result = service.importDocuments(next);
        assertThat(result.documents()).isEqualTo(1);
        assertThat(result.chunks()).isEqualTo(chunkIds(a).size());
        assertThat(vectorSnapshot(a)).isEqualTo(before);
        assertThat(chunkIds(b)).isEmpty();
        verify(gateway, never()).embedSystem(any());
        verify(vectors, never()).batchInsert(any());
        verify(vectors).deleteSystemByDocumentId(b);
    }

    @Test
    void 文档更名时导入新来源并清理旧来源的全部片段() throws Exception {
        service.importDocuments(fixture(Map.of("java/before.md", "Java文档正文")));
        long previous = documentId("java/before.md");
        var oldIds = chunkIds(previous);
        clearInvocations(gateway);
        service.importDocuments(fixture(Map.of("java/after.md", "Java文档正文")));
        assertThat(documentId("java/after.md")).isNotEqualTo(previous);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_documents WHERE id=?", Integer.class, previous)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM knowledge_vectors", Long.class)).isNotEmpty().doesNotContainAnyElementsOf(oldIds);
        verify(gateway).embedSystem(List.of("Java文档正文"));
    }

    @Test
    void 成功文档的实际片段缺失时在模型调用前终止() throws Exception {
        service.importDocuments(fixture(Map.of("java/a.md", "Java正文")));
        jdbc.update("DELETE FROM knowledge_vectors WHERE document_id=?", documentId("java/a.md"));
        var before = snapshot();
        var next = fixture(Map.of("java/a.md", "Java正文"));
        clearInvocations(gateway);
        assertThatThrownBy(() -> service.importDocuments(next)).isInstanceOf(IllegalStateException.class).hasMessageContaining("实际片段数量");
        assertThat(snapshot()).isEqualTo(before);
        verify(gateway, never()).embedSystem(any());
    }

    @Test
    void 向量生成期间已有文档变化时拒绝发布() throws Exception {
        var old = fixture(Map.of("java/a.md", "旧版正文"));
        service.importDocuments(old);
        long id = documentId("java/a.md");
        var before = vectorSnapshot(id);
        var next = fixture(Map.of("java/a.md", "新版正文"));
        doAnswer(call -> {
            jdbc.update("UPDATE knowledge_documents SET title='外部修改的标题' WHERE id=?", id);
            return call.callRealMethod();
        }).when(gateway).embedSystem(any());
        assertThatThrownBy(() -> service.importDocuments(next)).isInstanceOf(IllegalStateException.class).hasMessageContaining("变化");
        assertThat(vectorSnapshot(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT metadata->>'importRunId' FROM knowledge_documents WHERE id=?", String.class, id)).isEqualTo(old.runId());
        assertThat(jdbc.queryForObject("SELECT title FROM knowledge_documents WHERE id=?", String.class, id)).isEqualTo("外部修改的标题");
    }

    @Test
    void 旧片段删除数量异常时回滚替换并保留原内容() throws Exception {
        service.importDocuments(fixture(Map.of("java/a.md", "旧版正文")));
        var before = snapshot();
        var next = fixture(Map.of("java/a.md", "新版正文"));
        doAnswer(call -> {
            int deleted = sessions.getMapper(KnowledgeVectorMapper.class).deleteSystemByDocumentId(call.getArgument(0));
            return deleted + 1;
        }).when(vectors).deleteSystemByDocumentId(any());
        assertThatThrownBy(() -> service.importDocuments(next)).isInstanceOf(IllegalStateException.class).hasMessageContaining("删除数量");
        assertThat(snapshot()).isEqualTo(before);
    }

    private long documentId(String path) {
        return jdbc.queryForObject("SELECT id FROM knowledge_documents WHERE content=?", Long.class, "javaguide:docs/" + path);
    }

    private List<Long> chunkIds(long document) {
        return jdbc.queryForList("SELECT id FROM knowledge_vectors WHERE document_id=? ORDER BY chunk_index", Long.class, document);
    }

    private List<String> vectorSnapshot(long document) {
        return jdbc.queryForList("SELECT row_to_json(kv)::text FROM knowledge_vectors kv WHERE document_id=? ORDER BY chunk_index", String.class, document);
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT row_to_json(kd)::text AS document, (SELECT jsonb_agg(to_jsonb(kv) ORDER BY chunk_index)::text FROM knowledge_vectors kv WHERE kv.document_id=kd.id) AS vectors FROM knowledge_documents kd ORDER BY kd.id");
    }

    private void seedOtherSources() {
        long user = users.createPair().userA().getId();
        jdbc.update("INSERT INTO knowledge_documents(scope,user_id,type,content,title,metadata) VALUES (2,NULL,2,'other:system','其他系统资料','{}'),(3,NULL,2,'other:learning','学习资料','{}'),(1,?,1,'private-source','私有资料','{}')", user);
    }

    private SystemKnowledgeImportRequest fixture(Map<String, String> texts) throws Exception {
        return fixture(texts, "a".repeat(40), "");
    }

    private SystemKnowledgeImportRequest fixture(Map<String, String> texts, String commit, String titleSuffix) throws Exception {
        String run = "20261127T000000Z-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path directory = ROOT.resolve("versions").resolve(run);
        Files.createDirectories(directory);
        String repository = "https://github.com/Snailclimb/JavaGuide";
        List<Map<String, String>> docs = new ArrayList<>();
        for (var entry : new TreeMap<>(texts).entrySet()) {
            String path = "docs/" + entry.getKey();
            Path file = directory.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, entry.getValue());
            docs.add(Map.of("source_path", path, "output_path", path, "title", entry.getKey() + titleSuffix, "scope", "SYSTEM",
                    "content_hash", hash(Files.readAllBytes(file)), "source_url", repository + "/blob/" + commit + "/" + path));
        }
        byte[] manifest = json.writeValueAsBytes(Map.of("source_commit", commit, "repository", repository,
                "rules_hash", "b".repeat(64), "cleaner_hash", "c".repeat(64), "hash_algorithm", "SHA-256", "documents", docs));
        Files.write(directory.resolve("manifest.json"), manifest);
        Files.deleteIfExists(ROOT.resolve("current")); Files.createSymbolicLink(ROOT.resolve("current"), Path.of("versions", run));
        return new SystemKnowledgeImportRequest(run, hash(manifest));
    }

    private static Path createRoot() {
        try { return Files.createTempDirectory("zdmj-system-import-it-").toRealPath(); }
        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private static String hash(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
