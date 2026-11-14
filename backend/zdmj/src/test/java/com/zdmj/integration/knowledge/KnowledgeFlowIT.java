package com.zdmj.integration.knowledge;

import com.zdmj.common.context.CurrentActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        long alphaId = uploadId(token, "alpha.md", "固定知识片段甲");
        long betaId = uploadId(token, "beta.md", "固定知识片段乙");
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
        long documentId = uploadId(token, "private.md", "个人文档内容");
        var beforeDocument = jdbcTemplate.queryForMap("SELECT * FROM knowledge_documents WHERE id = ?", documentId);
        var beforeVectors = jdbcTemplate.queryForList(
                "SELECT * FROM knowledge_vectors WHERE document_id = ? ORDER BY id", documentId);
        assertThat(beforeVectors).isNotEmpty();
        CurrentActor otherActor = new CurrentActor(pair.userB().getId());

        assertThatThrownBy(() -> knowledgeEmbeddingService.vectorizeAndStore(otherActor, documentId))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode());
        ApiClient.assertProblem(api.delete(testJwt.authorize(pair.userB()), "/knowledge-document/" + documentId), 404, 8012);

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM knowledge_documents WHERE id = ?", documentId))
                .isEqualTo(beforeDocument);
        assertThat(jdbcTemplate.queryForList(
                "SELECT * FROM knowledge_vectors WHERE document_id = ? ORDER BY id", documentId))
                .isEqualTo(beforeVectors);
    }

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.zdmj.common.storage.FileUploadService files;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.zdmj.aiService.api.EmbeddingGateway embedding;

    @Autowired private org.springframework.core.env.Environment environment;
    @Autowired private com.zdmj.knowledgeService.service.KnowledgeDocumentService documents;

    @Test
    void 原文件保存下载和替换_返回原始字节且清理旧文件() throws Exception {
        var pair = testUsers.createPair();
        String token = testJwt.authorize(pair.userA());
        long id = uploadId(token, "项目说明.markdown", "原始文件内容");
        String key = jdbcTemplate.queryForObject("SELECT content FROM knowledge_documents WHERE id=?", String.class, id);
        assertThat(key).matches("user-" + pair.userA().getId() + "/[a-f0-9]{32}\\.md");
        assertThat(root().resolve(key)).exists();
        var result = api.get(token, "/knowledge-document/" + id + "/download");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("原始文件内容".getBytes(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment", "filename*", "%E9%A1%B9");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        String detail = ApiClient.requireOk(api.get(token, "/knowledge-document/" + id));
        assertThat(JsonPath.<String>read(detail, "$.data.content")).isEqualTo("/knowledge-document/" + id + "/download");
        assertThat(detail).doesNotContain(key);
        ApiClient.assertProblem(api.get(testJwt.authorize(pair.userB()), "/knowledge-document/" + id + "/download"), 404, 8012);
        var replace = multipart(ApiClient.PREFIX + "/knowledge-document").file(file("new.md", "替换后的原始文档内容"))
                .param("title", "新标题").param("type", "1").param("id", Long.toString(id))
                .with(request -> { request.setMethod("PUT"); return request; });
        ApiClient.requireOk(api.exchange(replace, token, null));
        assertThat(root().resolve(key)).doesNotExist();
        assertThat(api.get(token, "/knowledge-document/" + id + "/download").getResponse().getContentAsByteArray())
                .isEqualTo("替换后的原始文档内容".getBytes(StandardCharsets.UTF_8));
        String newKey = jdbcTemplate.queryForObject("SELECT content FROM knowledge_documents WHERE id=?", String.class, id);
        ApiClient.requireOk(api.delete(token, "/knowledge-document/" + id));
        assertThat(root().resolve(newKey)).doesNotExist();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_vectors WHERE document_id=?", Integer.class, id)).isZero();
    }

    @Test
    void 保存失败保留失败状态_嵌入失败仍可下载并显式重试() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE))
                .when(files).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        ApiClient.assertProblem(uploadResult(token, "failed.md", "保存失败"), 503, 1013);
        long failed = jdbcTemplate.queryForObject("SELECT id FROM knowledge_documents", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT file_status FROM knowledge_documents WHERE id=?", Integer.class, failed)).isEqualTo(2);
        ApiClient.requireOk(api.delete(token, "/knowledge-document/" + failed));
        org.mockito.Mockito.reset(files);
        org.mockito.Mockito.doThrow(new IllegalStateException("embedding unavailable"))
                .when(embedding).embed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
        ApiClient.assertProblem(uploadResult(token, "retry.md", "等待重新处理"), 500, 1008);
        long id = jdbcTemplate.queryForObject("SELECT id FROM knowledge_documents", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT embedding_status FROM knowledge_documents WHERE id=?", Integer.class, id)).isEqualTo(4);
        assertThat(api.get(token, "/knowledge-document/" + id + "/download").getResponse().getStatus()).isEqualTo(200);
        org.mockito.Mockito.reset(embedding);
        ApiClient.requireOk(api.post(token, "/knowledge-document/" + id + "/embedding", null));
        assertThat(jdbcTemplate.queryForObject("SELECT embedding_status FROM knowledge_documents WHERE id=?", Integer.class, id)).isEqualTo(3);
    }

    @Test
    void 删除失败保留删除中状态_重试删除与清空均清理文件() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long id = uploadId(token, "a.md", "原始文件的内容甲");
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE))
                .when(files).delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        ApiClient.assertProblem(api.delete(token, "/knowledge-document/" + id), 503, 1013);
        assertThat(jdbcTemplate.queryForObject("SELECT file_status FROM knowledge_documents WHERE id=?", Integer.class, id)).isEqualTo(3);
        ApiClient.assertProblem(api.get(token, "/knowledge-document/" + id + "/download"), 409, 8016);
        org.mockito.Mockito.reset(files);
        ApiClient.requireOk(api.delete(token, "/knowledge-document/" + id));
        uploadId(token, "b.md", "原始文件的内容乙");
        uploadId(token, "c.md", "原始文件的内容丙");
        ApiClient.requireOk(api.delete(token, "/knowledge"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_documents", Integer.class)).isZero();
        try (var paths = java.nio.file.Files.walk(root())) {
            assertThat(paths.filter(java.nio.file.Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void 非法上传与外部地址拒绝_不创建文档() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        ApiClient.assertProblem(uploadResult(token, "a.exe", "非法类型"), 400, 8009);
        ApiClient.assertProblem(api.post(token, "/knowledge-document", """
                {"title":"外部来源","type":1,"content":"https://evil.example/a.pdf"}
                """), 400, 1001);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_documents", Integer.class)).isZero();
    }

    @Test
    void 向量化期间删除文档_结束后不能重新插入向量() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = uploadId(token, "a.md", "并发测试内容");
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(embedding).embed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> knowledgeEmbeddingService.vectorizeAndStore(new CurrentActor(user.getId()), id));
            try {
                assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                ApiClient.assertProblem(api.post(token, "/knowledge-document/" + id + "/embedding", null), 409, 8016);
                var replacement = multipart(ApiClient.PREFIX + "/knowledge-document")
                        .file(file("replacement.md", "同时替换的原始文档内容"))
                        .param("title", "替换标题").param("type", "1").param("id", Long.toString(id))
                        .with(request -> { request.setMethod("PUT"); return request; });
                ApiClient.assertProblem(api.exchange(replacement, token, null), 409, 8016);
                ApiClient.requireOk(api.delete(token, "/knowledge-document/" + id));
            } finally { release.countDown(); }
            assertThatThrownBy(() -> future.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_vectors WHERE document_id=?", Integer.class, id)).isZero();
    }

    @Test
    void 新库和已有库字段升级均可重复执行() throws Exception {
        var script = java.nio.file.Path.of(System.getProperty("zdmj.repo.root"), "sql", "migrations", "20261112_drop_knowledge_vector_tasks.sql");
        jdbcTemplate.execute("ALTER TABLE knowledge_documents DROP COLUMN file_status");
        String sql = java.nio.file.Files.readString(script);
        jdbcTemplate.execute(sql);
        jdbcTemplate.execute(sql);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='knowledge_documents' AND column_name='file_status'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 替换失败恢复原文档_进程中断状态可重新处理和删除() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = uploadId(token, "before.md", "替换前的原始文档内容");
        String originalKey = jdbcTemplate.queryForObject("SELECT content FROM knowledge_documents WHERE id=?", String.class, id);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE))
                .when(files).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var replace = multipart(ApiClient.PREFIX + "/knowledge-document").file(file("after.md", "替换后的原始文档内容"))
                .param("title", "替换标题").param("type", "1").param("id", Long.toString(id))
                .with(request -> { request.setMethod("PUT"); return request; });
        var failed = api.exchange(replace, token, null);
        ApiClient.assertProblem(failed, 503, 1013);
        assertThat(JsonPath.<Number>read(failed.getResponse().getContentAsString(), "$.documentId").longValue()).isEqualTo(id);
        assertThat(root().resolve(originalKey)).exists();
        org.mockito.Mockito.reset(files);
        ApiClient.requireOk(api.post(token, "/knowledge-document/" + id + "/embedding", null));
        assertThat(api.get(token, "/knowledge-document/" + id + "/download").getResponse().getContentAsByteArray())
                .isEqualTo("替换前的原始文档内容".getBytes(StandardCharsets.UTF_8));
        assertThat(jdbcTemplate.queryForObject("SELECT jsonb_exists(metadata, 'pendingKey') FROM knowledge_documents WHERE id=?", Boolean.class, id)).isFalse();
        jdbcTemplate.update("UPDATE knowledge_documents SET embedding_status=2 WHERE id=?", id);
        long writing = uploadId(token, "writing.md", "进程中断的原文件内容");
        jdbcTemplate.update("UPDATE knowledge_documents SET file_status=0 WHERE id=?", writing);
        ((com.zdmj.knowledgeService.service.impl.KnowledgeDocumentServiceImpl) documents).recoverInterruptedOperations();
        assertThat(jdbcTemplate.queryForObject("SELECT embedding_status FROM knowledge_documents WHERE id=?", Integer.class, id)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT file_status FROM knowledge_documents WHERE id=?", Integer.class, writing)).isEqualTo(2);
        ApiClient.requireOk(api.post(token, "/knowledge-document/" + id + "/embedding", null));
        ApiClient.requireOk(api.delete(token, "/knowledge-document/" + writing));
    }

    @Test
    void 首次并发上传_只建立一个用户知识库() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(10, java.util.concurrent.TimeUnit.SECONDS); return uploadId(token, "first.md", "并发上传的第一份文档"); });
            var second = executor.submit(() -> { start.await(10, java.util.concurrent.TimeUnit.SECONDS); return uploadId(token, "second.md", "并发上传的第二份文档"); });
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS)).isNotEqualTo(second.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_bases WHERE user_id=?", Integer.class, user.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_documents WHERE user_id=? AND file_status=1 AND embedding_status=3", Integer.class, user.getId())).isEqualTo(2);
    }

    @Test
    void 原文件确认事务失败_保留失败记录和原文件供清理() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        org.mockito.Mockito.doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            jdbcTemplate.execute("CREATE FUNCTION reject_knowledge_ready() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.file_status=1 THEN RAISE EXCEPTION 'injected failure'; END IF; RETURN NEW; END $$");
            jdbcTemplate.execute("CREATE TRIGGER reject_knowledge_ready BEFORE UPDATE ON knowledge_documents FOR EACH ROW EXECUTE FUNCTION reject_knowledge_ready()");
            return result;
        }).when(files).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            var failure = uploadResult(token, "transaction.md", "数据库确认失败的原始文档");
            ApiClient.assertProblem(failure, 500, 1008);
            long id = JsonPath.<Number>read(failure.getResponse().getContentAsString(), "$.documentId").longValue();
            assertThat(jdbcTemplate.queryForObject("SELECT file_status FROM knowledge_documents WHERE id=?", Integer.class, id)).isEqualTo(2);
            String key = jdbcTemplate.queryForObject("SELECT content FROM knowledge_documents WHERE id=?", String.class, id);
            assertThat(root().resolve(key)).exists();
            ApiClient.requireOk(api.delete(token, "/knowledge-document/" + id));
            assertThat(root().resolve(key)).doesNotExist();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_knowledge_ready ON knowledge_documents");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_knowledge_ready()");
        }
    }

    @Test
    void 原文件备份与摘要检查_缺失或损坏阻止发布() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = uploadId(token, "legacy.md", "需要备份的原始文档内容");
        String key = jdbcTemplate.queryForObject("SELECT content FROM knowledge_documents WHERE id=?", String.class, id);
        assertThat(key).matches("user-" + user.getId() + "/[a-f0-9]{32}\\.md");
        byte[] source = java.nio.file.Files.readAllBytes(root().resolve(key));
        assertThat(api.get(token, "/knowledge-document/" + id + "/download").getResponse().getContentAsByteArray()).isEqualTo(source);
        var backups = java.nio.file.Files.createTempDirectory("zdmj-storage-backup-").toRealPath();
        assertThat(maintenance("check", backups)).contains("原文件检查通过");
        assertThat(maintenance("backup", backups)).contains("备份完成");
        try (var paths = java.nio.file.Files.walk(backups)) {
            assertThat(paths.filter(java.nio.file.Files::isRegularFile).map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("database.dump", "knowledge.tar");
        }
        java.nio.file.Path snapshot;
        try (var paths = java.nio.file.Files.list(backups)) { snapshot = paths.findFirst().orElseThrow(); }
        var postgres = com.zdmj.testsupport.TestContainers.POSTGRES;
        var createDb = postgres.execInContainer("createdb", "-U", "test", "storage_restore");
        assertThat(createDb.getExitCode()).isZero();
        var restoreOutput = java.nio.file.Files.createTempFile("zdmj-restore-", ".log");
        var restore = new ProcessBuilder("docker", "exec", "-i", postgres.getContainerId(), "pg_restore", "--exit-on-error", "--no-owner", "-U", "test", "-d", "storage_restore")
                .redirectInput(snapshot.resolve("database.dump").toFile()).redirectErrorStream(true).redirectOutput(restoreOutput.toFile()).start();
        assertThat(restore.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(restore.exitValue()).as(java.nio.file.Files.readString(restoreOutput)).isZero();
        assertThat(postgres.execInContainer("psql", "-At", "-U", "test", "-d", "storage_restore", "-c", "SELECT content FROM knowledge_documents WHERE id=" + id).getStdout().trim()).isEqualTo(key);
        var restoredFiles = java.nio.file.Files.createTempDirectory("zdmj-files-restored-");
        var extract = new ProcessBuilder("tar", "-xf", snapshot.resolve("knowledge.tar").toString(), "-C", restoredFiles.toString()).start();
        assertThat(extract.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(extract.exitValue()).isZero();
        assertThat(java.nio.file.Files.readAllBytes(restoredFiles.resolve("knowledge").resolve(key))).isEqualTo(source);
        java.nio.file.Files.writeString(root().resolve(key), "损坏");
        assertThat(maintenance("check", backups)).contains("摘要不符");
    }

    /** 维护命令使用真实隔离 PostgreSQL；Compose 查询和宿主机 UID 由测试替身提供。 */
    private String maintenance(String action, java.nio.file.Path backups) throws Exception {
        var repository = java.nio.file.Path.of(System.getProperty("zdmj.repo.root"));
        String shell = java.nio.file.Files.readString(repository.resolve("deploy/deploy_backend.sh"));
        String script = shell.split("<<'PYTHON'\\n", 2)[1].split("\\nPYTHON", 2)[0];
        String bootstrap = """
                import os, pathlib, subprocess, json
                actual_run = subprocess.run
                def isolated_run(command, **options):
                    if 'config' in command:
                        return subprocess.CompletedProcess(command, 0, json.dumps({'services': {'backend': {'volumes': [{'source': os.environ['STORAGE_TEST_ROOT'], 'target': '/app/data/knowledge'}]}}}))
                    if 'ps' in command:
                        return subprocess.CompletedProcess(command, 0, '')
                    if 'psql' in command[-1]:
                        command = ['docker','exec','-i',os.environ['STORAGE_TEST_POSTGRES'],'psql','-X','-v','ON_ERROR_STOP=1','-At','-U','test','-d','test']
                    elif 'pg_dump' in command[-1]:
                        command = ['docker','exec','-i',os.environ['STORAGE_TEST_POSTGRES'],'pg_dump','-Fc','-U','test','-d','test']
                    return actual_run(command, **options)
                subprocess.run = isolated_run
                original_stat = pathlib.Path.stat
                def isolated_stat(path, **options):
                    result = original_stat(path, **options)
                    if str(path) == os.environ['STORAGE_TEST_ROOT']:
                        items = list(result); items[4] = 10001; items[5] = 10001; items[0] = (items[0] & ~0o777) | 0o750
                        return os.stat_result(items)
                    return result
                pathlib.Path.stat = isolated_stat
                os.chown = lambda *arguments: None
                """;
        var builder = new ProcessBuilder("python3", "-c", bootstrap + script, repository.toString(), "test.env", action);
        builder.environment().put("STORAGE_TEST_ROOT", root().toString());
        builder.environment().put("STORAGE_TEST_POSTGRES", com.zdmj.testsupport.TestContainers.POSTGRES.getContainerId());
        builder.environment().put("KNOWLEDGE_BACKUP_ROOT", backups.toString());
        var output = java.nio.file.Files.createTempFile("zdmj-maintenance-", ".log");
        var process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        assertThat(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        return java.nio.file.Files.readString(output);
    }

    private java.nio.file.Path root() { return java.nio.file.Path.of(environment.getRequiredProperty("knowledge.storage.root")); }
    private MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "text/markdown", text.getBytes(StandardCharsets.UTF_8));
    }
    private MvcResult uploadResult(String token, String name, String text) throws Exception {
        return mockMvc.perform(multipart(ApiClient.PREFIX + "/knowledge-document").file(file(name, text))
                .param("title", "知识文档").param("type", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }
    private long uploadId(String token, String name, String text) throws Exception {
        return ApiClient.dataId(uploadResult(token, name, text));
    }
}
