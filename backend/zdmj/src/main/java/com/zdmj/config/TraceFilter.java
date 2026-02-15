package com.zdmj.config;

import com.zdmj.common.util.TraceUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 分布式追踪过滤器
 * 用于生成和传递TraceId，便于微服务化后的链路追踪
 */
@Component
@Order(1) // 确保在其他过滤器之前执行
public class TraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            // 从请求头获取TraceId，如果没有则生成新的
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = TraceUtil.generateTraceId();
            }
            
            // 设置到MDC和响应头
            TraceUtil.setTraceId(traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            
            // 继续过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清除MDC，避免内存泄漏
            TraceUtil.clearAll();
        }
    }
}
