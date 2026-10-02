package com.zdmj.testsupport;

import java.sql.Connection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 每个集成测试开始前清空业务表并清空当前 Redis 库。
 * 目标端口不属于测试容器时立即终止，避免清理开发数据。
 */
public class DatabaseCleaner {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 核对 JDBC URL 与 Redis 端口后，清空 public 表并重置序列，再执行 FLUSHDB。
     */
    public void clean() {
        String jdbcUrl = currentJdbcUrl();
        int redisPort = currentRedisPort();
        verifyTargets(jdbcUrl, redisPort, TestContainers.postgresPort(),
                TestContainers.POSTGRES.getDatabaseName(), TestContainers.redisPort());
        truncateTables();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    /**
     * @param jdbcUrl 当前连接的 JDBC URL
     * @param redisPort 当前 Redis 端口
     * @param expectedPostgresPort 测试容器映射端口
     * @param expectedDatabase 测试容器数据库名
     * @param expectedRedisPort 测试容器 Redis 映射端口
     */
    public static void verifyTargets(String jdbcUrl, int redisPort, int expectedPostgresPort,
            String expectedDatabase, int expectedRedisPort) {
        if (jdbcUrl == null
                || !jdbcUrl.contains(":" + expectedPostgresPort + "/")
                || !jdbcUrl.contains("/" + expectedDatabase)) {
            throw new IllegalStateException("拒绝清理非测试数据库: url=" + jdbcUrl);
        }
        if (redisPort != expectedRedisPort) {
            throw new IllegalStateException("拒绝清理非测试 Redis: port=" + redisPort
                    + ", expected=" + expectedRedisPort);
        }
    }

    private String currentJdbcUrl() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            return connection.getMetaData().getURL();
        } catch (Exception e) {
            throw new IllegalStateException("无法读取测试数据源 URL", e);
        }
    }

    private int currentRedisPort() {
        if (!(redisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException("测试 Redis 连接不是 LettuceConnectionFactory");
        }
        return lettuce.getPort();
    }

    private void truncateTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        if (tables.isEmpty()) {
            throw new IllegalStateException("测试库 public 中没有业务表");
        }
        for (String table : tables) {
            if (!TABLE_NAME.matcher(table).matches()) {
                throw new IllegalStateException("拒绝清理异常表名: " + table);
            }
        }
        String sql = tables.stream()
                .map(table -> "\"" + table + "\"")
                .collect(Collectors.joining(", ", "TRUNCATE TABLE ", " RESTART IDENTITY CASCADE"));
        jdbcTemplate.execute(sql);
    }
}
