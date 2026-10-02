package com.zdmj.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 测试 JVM 内共享的 PostgreSQL 与 Redis。镜像与 deploy/docker-compose.yml 一致。
 */
public final class TestContainers {

    public static final String JWT_SECRET = "zdmj-test-jwt-secret-0123456789abcdef";

    /** 偶数位 hex，仅用于测试进程内的 API Key 加密。 */
    public static final String ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef";

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"));

    public static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
        SchemaInitializer.apply(POSTGRES);
    }

    private TestContainers() {
    }

    public static int postgresPort() {
        return POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    public static int redisPort() {
        return REDIS.getMappedPort(6379);
    }
}
