package com.zdmj.testsupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 用仓库根目录的 sql/pgsql.sql 初始化测试库，不复制第二份 Schema。
 */
public final class SchemaInitializer {

    private static volatile boolean applied;

    private SchemaInitializer() {
    }

    public static void apply(PostgreSQLContainer<?> postgres) {
        if (applied) {
            return;
        }
        synchronized (SchemaInitializer.class) {
            if (applied) {
                return;
            }
            Path script = resolveScript();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new FileSystemResource(script), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("测试 Schema 初始化失败: " + script, e);
            }
            applied = true;
        }
    }

    private static Path resolveScript() {
        String raw = System.getProperty("zdmj.repo.root");
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("测试 JVM 缺少 zdmj.repo.root，拒绝初始化 Schema");
        }
        Path script = Path.of(raw).toAbsolutePath().normalize().resolve("sql/pgsql.sql");
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("找不到 Schema 脚本: " + script);
        }
        return script;
    }
}
