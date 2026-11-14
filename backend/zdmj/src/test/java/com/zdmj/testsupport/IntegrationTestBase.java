package com.zdmj.testsupport;

import org.junit.jupiter.api.BeforeEach;

import com.zdmj.common.util.DateTimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 集成测试基类。容器、Schema 与外部服务替身在此汇合，每个用例开始前清理数据。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ExternalAiStubConfig.class)
public abstract class IntegrationTestBase {

    protected final DatabaseCleaner databaseCleaner;

    private static final java.nio.file.Path STORAGE_ROOT = createStorageRoot();

    private static java.nio.file.Path createStorageRoot() {
        try {
            return java.nio.file.Files.createTempDirectory("zdmj-knowledge-it-").toRealPath();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Autowired
    private MutableClock mutableClock;

    protected IntegrationTestBase(DatabaseCleaner databaseCleaner) {
        this.databaseCleaner = databaseCleaner;
    }

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String jdbcUrl = TestContainers.POSTGRES.getJdbcUrl();
            String separator = jdbcUrl.contains("?") ? "&" : "?";
            return jdbcUrl + separator + "stringtype=unspecified&TimeZone=Asia/Shanghai&sslmode=disable";
        });
        registry.add("spring.datasource.username", TestContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestContainers.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", TestContainers.REDIS::getHost);
        registry.add("spring.data.redis.port", TestContainers::redisPort);
        registry.add("knowledge.storage.root", () -> STORAGE_ROOT.toString());
        registry.add("app.jwt.secret", () -> TestContainers.JWT_SECRET);
        registry.add("app.ai.user-llm.encryption-key", () -> TestContainers.ENCRYPTION_KEY);
    }

    @BeforeEach
    void cleanDatabaseAndRedis() {
        databaseCleaner.clean();
        try (var paths = java.nio.file.Files.walk(STORAGE_ROOT)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!path.equals(STORAGE_ROOT)) { java.nio.file.Files.delete(path); }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法清理隔离测试目录", exception);
        }
        DateTimeUtil.bind(mutableClock);
        mutableClock.resetToSystem();
        ExternalAiStubConfig.clearModelTransactionObservations();
    }
}
