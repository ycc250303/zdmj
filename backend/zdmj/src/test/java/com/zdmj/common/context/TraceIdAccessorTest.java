package com.zdmj.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceIdAccessorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 服务加载注册_作用域恢复追踪字段且保留其他日志字段() {
        assertThat(ContextRegistry.getInstance().getThreadLocalAccessors())
                .filteredOn(accessor -> accessor.key().equals(TraceIdAccessor.KEY))
                .singleElement().isInstanceOf(TraceIdAccessor.class);
        MDC.put("traceId", "request-trace");
        var snapshot = ContextSnapshotFactory.builder().build().captureAll();
        MDC.put("traceId", "worker-trace");
        MDC.put("other", "preserved");

        try (var scope = snapshot.setThreadLocals()) {
            assertThat(MDC.get("traceId")).isEqualTo("request-trace");
            assertThat(MDC.get("other")).isEqualTo("preserved");
        }

        assertThat(MDC.get("traceId")).isEqualTo("worker-trace");
        assertThat(MDC.get("other")).isEqualTo("preserved");
        MDC.remove("traceId");
        try (var scope = snapshot.setThreadLocals()) {
            assertThat(MDC.get("traceId")).isEqualTo("request-trace");
        }
        assertThat(MDC.get("traceId")).isNull();
    }
}
