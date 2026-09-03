package com.zdmj.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * 在进入 MVC 之前写出 RFC 9457 Problem Details（Filter / Security 入口共用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemDetailHttpWriter {

    private final ObjectMapper objectMapper;

    /**
     * 按 {@link ErrorCode} 绑定的 HTTP 状态写出响应。响应已提交时跳过。
     *
     * @return 是否成功写出
     */
    public boolean write(HttpServletResponse response, ErrorCode errorCode) {
        if (response.isCommitted()) {
            log.debug("响应已提交，跳过 Problem Details: code={}", errorCode.getCode());
            return false;
        }
        try {
            ProblemDetail problem = ProblemDetailSupport.of(errorCode);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setStatus(errorCode.getHttpStatus().value());
            response.setContentType(ProblemDetailSupport.PROBLEM_JSON.toString());
            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return true;
        } catch (Exception e) {
            log.debug("无法发送错误响应: {}", e.getMessage());
            return false;
        }
    }
}
