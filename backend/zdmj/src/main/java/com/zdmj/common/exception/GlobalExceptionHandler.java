package com.zdmj.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全局异常处理：业务异常与 Spring MVC 异常统一为 RFC 9457 + 业务 {@code code}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException ex, WebRequest request) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return handleExceptionInternal(
                ex,
                ProblemDetailSupport.of(ex.getErrorCode(), ex.getMessage()),
                new HttpHeaders(),
                ex.getErrorCode().getHttpStatus(),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", detail);
        return validationProblem(ex, detail, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", detail);
        return validationProblem(ex, detail, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUncaughtException(Exception ex, WebRequest request) {
        log.error("未捕获异常: ", ex);
        return handleExceptionInternal(
                ex,
                ProblemDetailSupport.of(ErrorCode.SYSTEM_EXCEPTION),
                new HttpHeaders(),
                ErrorCode.SYSTEM_EXCEPTION.getHttpStatus(),
                request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", detail);
        return validationProblem(ex, detail, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String detail = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.toString())
                .collect(Collectors.joining(", "));
        log.warn("方法参数校验失败: {}", detail);
        return validationProblem(ex, detail, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.warn("请求体不可读: {}", ex.getMessage());
        return handleExceptionInternal(
                ex, ProblemDetailSupport.of(ErrorCode.REQUEST_BODY_ERROR), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            applyInstance(problem, request);
            if (!hasCode(problem)) {
                ErrorCode errorCode = errorCodeForStatus(statusCode);
                problem.setProperty("code", errorCode.getCode());
                problem.setTitle(errorCode.getMessage());
            }
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        // ErrorResponse 可能给出只读 HttpHeaders.EMPTY，必须拷贝后再写 Content-Type
        responseHeaders.setContentType(ProblemDetailSupport.PROBLEM_JSON);
        return new ResponseEntity<>(body, responseHeaders, statusCode);
    }

    private ResponseEntity<Object> validationProblem(Exception ex, String detail, WebRequest request) {
        String message = (detail == null || detail.isBlank())
                ? ErrorCode.VALIDATION_ERROR.getMessage()
                : ErrorCode.VALIDATION_ERROR.getMessage() + ": " + detail;
        return handleExceptionInternal(
                ex,
                ProblemDetailSupport.of(ErrorCode.VALIDATION_ERROR, message),
                new HttpHeaders(),
                ErrorCode.VALIDATION_ERROR.getHttpStatus(),
                request);
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static ErrorCode errorCodeForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.USER_NOT_LOGIN;
            case 403 -> ErrorCode.NO_PERMISSION;
            case 404, 405 -> ErrorCode.REQUEST_METHOD_NOT_SUPPORTED;
            case 413 -> ErrorCode.FILE_SIZE_EXCEEDED;
            default -> status.is5xxServerError() ? ErrorCode.SYSTEM_EXCEPTION : ErrorCode.VALIDATION_ERROR;
        };
    }

    private static void applyInstance(ProblemDetail problem, WebRequest request) {
        URI instance = getInstanceUri(request);
        if (instance != null) {
            problem.setInstance(instance);
        }
    }

    @Nullable
    private static URI getInstanceUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return URI.create(servletWebRequest.getRequest().getRequestURI());
        }
        return null;
    }
}
