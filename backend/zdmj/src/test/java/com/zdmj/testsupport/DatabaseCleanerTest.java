package com.zdmj.testsupport;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseCleanerTest {

    @Test
    void 校验目标_端口或库名不属于测试容器_立即终止() {
        assertThatThrownBy(() -> DatabaseCleaner.verifyTargets(
                "jdbc:postgresql://localhost:5432/zdmj", 6379, 55001, "test", 55002))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝清理非测试数据库");

        assertThatThrownBy(() -> DatabaseCleaner.verifyTargets(
                "jdbc:postgresql://localhost:55001/test", 6379, 55001, "test", 55002))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝清理非测试 Redis");
    }
}
