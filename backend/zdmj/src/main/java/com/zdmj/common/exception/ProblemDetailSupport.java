package com.zdmj.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * RFC 9457 Problem Details 响应构建工具。
 *
 * <p>业务错误码 {@code code} 写入 {@code properties}，由 Spring {@code ProblemDetailJacksonMixin}
 * 展开为 JSON 顶层字段，便于客户端与原有 {@code Result.code} 逻辑对齐。</p>
 */
public final class ProblemDetailSupport {

    public static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    private ProblemDetailSupport() {
    }

    public static ProblemDetail of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getHttpStatus(), detail);
        problem.setTitle(errorCode.getMessage());
        problem.setProperty("code", errorCode.getCode());
        return problem;
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail, HttpStatus httpStatus) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(httpStatus, detail);
        problem.setTitle(errorCode.getMessage());
        problem.setProperty("code", errorCode.getCode());
        return problem;
    }

    public static ProblemDetail of(int code, HttpStatus httpStatus, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(httpStatus, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
