package com.zdmj.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 分布式追踪工具类
 * 用于生成和传递TraceId，便于微服务化后的链路追踪
 */
public class TraceUtil {

    private static final String TRACE_ID_KEY = "X-Trace-Id";

    /**
     * 获取当前请求的TraceId
     * 
     * @return TraceId，如果不存在则生成一个新的
     */
    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 设置TraceId
     * 
     * @param traceId TraceId
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 生成新的TraceId
     * 
     * @return TraceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 清除TraceId
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 清除所有MDC内容
     */
    public static void clearAll() {
        MDC.clear();
    }
}
