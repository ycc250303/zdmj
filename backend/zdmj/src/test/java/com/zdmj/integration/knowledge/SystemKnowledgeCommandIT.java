package com.zdmj.integration.knowledge;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zdmj.knowledgeService.config.SystemKnowledgeImportApplication;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportRequest;
import com.zdmj.knowledgeService.service.SystemKnowledgeImportService;
import com.zdmj.testsupport.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/** 验证独立上下文与可执行JAR，只访问隔离数据库和本地嵌入替身。 */
class SystemKnowledgeCommandIT extends IntegrationTestBase {
    @TempDir Path root;
    @Autowired ApplicationContext web;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired SystemKnowledgeCommandIT(DatabaseCleaner cleaner) { super(cleaner); }

    @Test
    void 独立上下文隔离业务组件且可执行JAR按固定契约退出() throws Exception {
        assertThat(web.getBeansOfType(SystemKnowledgeImportService.class)).isEmpty();
        assertThat(web.getBeansOfType(SystemKnowledgeImportApplication.class)).isEmpty();
        var request = fixture();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            var body = json.readTree(exchange.getRequestBody());
            calls.incrementAndGet();
            assertThat(body.path("model").asText()).isEqualTo("text-embedding-v4");
            assertThat(body.path("dimensions").asInt()).isEqualTo(1024);
            List<Object> data = new ArrayList<>();
            for (int i = 0; i < body.path("input").size(); i++) {
                var values = new float[1024]; values[0] = 1;
                data.add(Map.of("object", "embedding", "index", i, "embedding", values));
            }
            byte[] response = json.writeValueAsBytes(Map.of("object", "list", "data", data, "model", "text-embedding-v4",
                    "usage", Map.of("prompt_tokens", 1, "total_tokens", 1)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            try (var context = SystemKnowledgeImportApplication.application().run(
                    "--spring.config.import=optional:file:src/test/resources/empty.env[.properties]",
                    "--spring.datasource.url=" + jdbcUrl(),
                    "--spring.datasource.username=" + TestContainers.POSTGRES.getUsername(),
                    "--spring.datasource.password=" + TestContainers.POSTGRES.getPassword(),
                    "--spring.ai.openai.base-url=" + base, "--spring.ai.openai.api-key=test-only",
                    "--knowledge.system-import.root=" + root.toRealPath(),
                    "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl")) {
                assertThat(context).isNotInstanceOf(WebApplicationContext.class);
                assertThat(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration().getMapperRegistry().getMappers())
                        .allMatch(type -> type.getPackageName().equals("com.zdmj.knowledgeService.mapper"));
                assertThat(context.getBeanDefinitionNames()).noneMatch(name -> name.contains("SecurityFilter") || name.contains("redisTemplate")
                        || name.contains("mailSender") || name.contains("knowledgeDocumentServiceImpl") || name.contains("messageServiceImpl"));
                assertThat(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).isEmpty();
                assertThat(context.getBeansOfType(org.springframework.data.redis.connection.RedisConnectionFactory.class)).isEmpty();
                assertThat(context.getBeansOfType(org.springframework.mail.javamail.JavaMailSender.class)).isEmpty();
                assertThat(context.getBean(SystemKnowledgeImportService.class)).isNotNull();
            }
            assertThat(calls).hasValue(0);
            String output = process(request, base, true);
            assertThat(output.lines().filter(line -> line.startsWith("SYSTEM_KNOWLEDGE_IMPORT_RESULT=")).count()).isEqualTo(1);
            var result = json.readTree(output.lines().filter(line -> line.startsWith("SYSTEM_KNOWLEDGE_IMPORT_RESULT=")).findFirst().orElseThrow().split("=", 2)[1]);
            assertThat(result.path("runId").asText()).isEqualTo(request.runId());
            assertThat(result.path("documents").asInt()).isEqualTo(1);
            assertThat(output).doesNotContain("并发与数据库知识资料");
            assertThat(calls).hasValue(1);
            process(new SystemKnowledgeImportRequest(request.runId(), "0".repeat(64)), base, false);
            assertThat(calls).hasValue(1);
        } finally { server.stop(0); }
    }

    private static String jdbcUrl() {
        String url = TestContainers.POSTGRES.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
    }

    private String process(SystemKnowledgeImportRequest request, String base, boolean success) throws Exception {
        Path jar = Path.of("target/zdmj-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(jar).isRegularFile();
        Path log = Files.createTempFile(root, "command-", ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Dloader.main=com.zdmj.knowledgeService.config.SystemKnowledgeImportApplication", "-cp", jar.toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher", "--run-id=" + request.runId(), "--manifest-hash=" + request.manifestHash());
        var env = builder.environment();
        env.put("ZDMJ_ENV_FILE", Path.of("src/test/resources/empty.env").toAbsolutePath().toString());
        env.put("SPRING_DATASOURCE_URL", jdbcUrl());
        env.put("SPRING_DATASOURCE_USERNAME", TestContainers.POSTGRES.getUsername());
        env.put("SPRING_DATASOURCE_PASSWORD", TestContainers.POSTGRES.getPassword());
        env.put("SPRING_AI_OPENAI_BASE_URL", base); env.put("DASHSCOPE_API_KEY", "test-only");
        env.put("KNOWLEDGE_SYSTEM_IMPORT_ROOT", root.toRealPath().toString());
        env.remove("SPRING_PROFILES_ACTIVE");
        var process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean ended = process.waitFor(45, TimeUnit.SECONDS);
        if (!ended) { process.destroyForcibly(); }
        assertThat(ended).as(Files.readString(log)).isTrue();
        String output = Files.readString(log);
        if (success) { assertThat(process.exitValue()).as(output).isZero(); }
        else { assertThat(process.exitValue()).as(output).isNotZero(); assertThat(output).doesNotContain("SYSTEM_KNOWLEDGE_IMPORT_RESULT="); }
        return output;
    }

    private SystemKnowledgeImportRequest fixture() throws Exception {
        String run = "20261127T000000Z-012345abcdef", repo = "https://github.com/Snailclimb/JavaGuide";
        String text = "# Java\n并发与数据库知识资料", commit = "a".repeat(40);
        Path version = root.resolve("versions").resolve(run); Files.createDirectories(version.resolve("docs/java"));
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(version.resolve("docs/java/test.md"), bytes);
        byte[] manifest = json.writeValueAsBytes(Map.of("source_commit", commit, "repository", repo, "rules_hash", "b".repeat(64),
                "cleaner_hash", "c".repeat(64), "hash_algorithm", "SHA-256", "documents", List.of(Map.of("source_path", "docs/java/test.md",
                        "output_path", "docs/java/test.md", "scope", "SYSTEM", "title", "Java", "content_hash", hash(bytes),
                        "source_url", repo + "/blob/" + commit + "/docs/java/test.md"))));
        Files.write(version.resolve("manifest.json"), manifest);
        Files.deleteIfExists(root.resolve("current"));
        Files.createSymbolicLink(root.resolve("current"), Path.of("versions", run));
        return new SystemKnowledgeImportRequest(run, hash(manifest));
    }
    private static String hash(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
