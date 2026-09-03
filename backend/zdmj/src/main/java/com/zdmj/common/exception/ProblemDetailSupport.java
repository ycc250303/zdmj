package com.zdmj.common.exception;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * RFC 9457 Problem Details 构建。业务码 {@code code} 写入 properties，由
 * {@code ProblemDetailJacksonMixin} 展开为 JSON 顶层字段。
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
}
