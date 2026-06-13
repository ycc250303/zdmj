package com.zdmj.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全局异常处理器（RFC 9457 Problem Details + 业务错误码 {@code code}）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException ex, WebRequest request) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        HttpStatus status = ErrorCode.httpStatusOf(ex.getCode());
        ProblemDetail problem = ProblemDetailSupport.of(ex.getCode(), status, ex.getMessage());
        applyInstance(problem, request);
        return response(problem, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", detail);
        return validationProblem(detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("非法参数: {}", ex.getMessage());
        return validationProblem(ex.getMessage(), request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex, WebRequest request) {
        if (ex instanceof MethodArgumentNotValidException) {
            return handleMethodArgumentNotValid(
                    (MethodArgumentNotValidException) ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
        }
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", detail);
        return validationProblem(detail, request);
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
        return validationProblem(detail, request);
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
        return validationProblem(detail, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.warn("缺少必需的请求参数: {} (类型: {})", ex.getParameterName(), ex.getParameterType());
        return validationProblem("缺少参数 " + ex.getParameterName(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Required request body is missing")
                    || message.contains("I/O error while reading input message")
                    || message.contains("Required request body")) {
                log.warn("请求体为空");
                return problem(ErrorCode.REQUEST_BODY_ERROR, request);
            }
            if (message.contains("LocalDate") || message.contains("LocalDateTime")) {
                log.warn("日期格式错误: {}", message);
                return problem(ErrorCode.DATE_FORMAT_ERROR, request);
            }
            if (message.contains("Cannot deserialize")) {
                log.warn("JSON反序列化失败: {}", message);
                return problem(ErrorCode.VALIDATION_ERROR, request);
            }
            if (message.contains("JSON parse error") || message.contains("Unexpected character")) {
                log.warn("JSON格式错误: {}", message);
                return problem(ErrorCode.REQUEST_BODY_ERROR, request);
            }
        }
        log.warn("JSON解析失败: {}", message);
        return validationProblem(message != null ? message : "未知错误", request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            @NonNull org.springframework.beans.TypeMismatchException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知类型";
        log.warn("请求参数类型不匹配: {} = {} (期望类型: {})", ex.getPropertyName(), ex.getValue(), requiredType);
        return validationProblem(ex.getPropertyName() + " 应为 " + requiredType + " 类型", request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            @NonNull org.springframework.web.HttpRequestMethodNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.warn("请求方法不支持: method={}, supported={}", ex.getMethod(), ex.getSupportedHttpMethods());
        String detail = ErrorCode.REQUEST_METHOD_NOT_SUPPORTED.getMessage() + ": " + ex.getMethod();
        ProblemDetail problem = ProblemDetailSupport.of(
                ErrorCode.REQUEST_METHOD_NOT_SUPPORTED, detail, HttpStatus.METHOD_NOT_ALLOWED);
        applyInstance(problem, request);
        return response(problem, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            @NonNull org.springframework.web.servlet.resource.NoResourceFoundException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.warn("接口路径不存在: {}", ex.getResourcePath());
        return problem(ErrorCode.REQUEST_METHOD_NOT_SUPPORTED, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            @NonNull MaxUploadSizeExceededException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.warn("上传文件大小超限: {}", ex.getMessage());
        return problem(ErrorCode.FILE_SIZE_EXCEEDED, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request) {
        headers.setContentType(ProblemDetailSupport.PROBLEM_JSON);
        return new ResponseEntity<>(body, headers, statusCode);
    }

    private ResponseEntity<Object> validationProblem(String detail, WebRequest request) {
        String message = ErrorCode.VALIDATION_ERROR.getMessage() + ": " + sanitizeValidationDetail(detail);
        ProblemDetail problem = ProblemDetailSupport.of(ErrorCode.VALIDATION_ERROR, message);
        applyInstance(problem, request);
        return response(problem, ErrorCode.VALIDATION_ERROR.getHttpStatus());
    }

    private static String sanitizeValidationDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "请求参数不合法";
        }
        if (detail.contains("invalid comparison") || detail.contains("com.zdmj.")) {
            return "请求参数不合法";
        }
        return detail;
    }

    private ResponseEntity<Object> problem(ErrorCode errorCode, WebRequest request) {
        ProblemDetail problem = ProblemDetailSupport.of(errorCode);
        applyInstance(problem, request);
        return response(problem, errorCode.getHttpStatus());
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

    private static ResponseEntity<Object> response(ProblemDetail problem, HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(ProblemDetailSupport.PROBLEM_JSON)
                .body(problem);
    }
}
