package com.zdmj.common.context;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * 在 Reactor Context 与日志 MDC 之间传递请求编号，由 ServiceLoader 注册。
 * 只维护追踪字段，执行范围结束后由上下文传播组件恢复原线程值。
 */
public final class TraceIdAccessor implements ThreadLocalAccessor<String> {

    public static final String KEY = "traceId";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public String getValue() {
        return MDC.get(KEY);
    }

    @Override
    public void setValue(String value) {
        MDC.put(KEY, value);
    }

    @Override
    public void setValue() {
        MDC.remove(KEY);
    }
}
