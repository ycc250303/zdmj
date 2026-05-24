package com.zdmj.common.exception;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * 兜底异常处理：{@link ResponseEntityExceptionHandler} 未覆盖的异常统一转为 Problem Details。
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class UncaughtExceptionAdvice {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUncaughtException(Exception ex, WebRequest request) {
        log.error("未捕获异常: ", ex);
        ProblemDetail problem = ProblemDetailSupport.of(ErrorCode.SYSTEM_EXCEPTION);
        URI instance = getInstanceUri(request);
        if (instance != null) {
            problem.setInstance(instance);
        }
        return ResponseEntity.status(ErrorCode.SYSTEM_EXCEPTION.getHttpStatus())
                .contentType(ProblemDetailSupport.PROBLEM_JSON)
                .body(problem);
    }

    @Nullable
    private static URI getInstanceUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return URI.create(servletWebRequest.getRequest().getRequestURI());
        }
        return null;
    }
}
