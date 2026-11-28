package com.zdmj.common.config;

import java.io.IOException;
import java.util.UUID;
import com.zdmj.common.context.TraceIdAccessor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为 HTTP 请求建立日志关联编号；错误分发复用编号，执行结束后恢复线程日志上下文。
 */
@Slf4j
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";

    /** 读取过滤器已建立的编号；缺少编号表示入口配置缺陷，立即终止处理。 */
    public static String requireTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (!(traceId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("HTTP 请求缺少日志关联编号");
        }
        return value;
    }

    /**
     * 后端生成编号并写入请求属性、响应头与 MDC。
     * 
     * @param request     请求对象
     * @param response    响应对象
     * @param filterChain 过滤器链
     * @throws ServletException 异常
     * @throws IOException      异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 1. 获取id
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        }
        String previousTraceId = MDC.get(TraceIdAccessor.KEY);
        MDC.put(TraceIdAccessor.KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            log.info("HTTP 请求开始 method={} path={} dispatcher={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getDispatcherType());
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                if (completed && request.isAsyncStarted()) {
                    log.info("HTTP 请求进入异步处理 method={} path={} dispatcher={} elapsedMs={}",
                            request.getMethod(), request.getRequestURI(), request.getDispatcherType(), elapsedMs);
                } else if (completed) {
                    log.info("HTTP 请求结束 method={} path={} dispatcher={} status={} elapsedMs={}",
                            request.getMethod(), request.getRequestURI(), request.getDispatcherType(),
                            response.getStatus(), elapsedMs);
                } else {
                    log.warn("HTTP 请求异常终止 method={} path={} dispatcher={} elapsedMs={}",
                            request.getMethod(), request.getRequestURI(), request.getDispatcherType(), elapsedMs);
                }
            } finally {
                if (previousTraceId == null) {
                    MDC.remove(TraceIdAccessor.KEY);
                } else {
                    MDC.put(TraceIdAccessor.KEY, previousTraceId);
                }
            }
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
