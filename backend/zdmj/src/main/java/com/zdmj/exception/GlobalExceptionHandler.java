package com.zdmj.exception;

import com.zdmj.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * 
     * @param e 业务异常
     * @return Result对象
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理Python服务异常
     * 
     * @param e Python服务异常
     * @return Result对象
     */
    @ExceptionHandler(PythonServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handlePythonServiceException(PythonServiceException e) {
        log.error("Python服务异常: [{}] {}", e.getCode(), e.getMessage(), e);
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@RequestBody参数校验）
     * 
     * @param e 参数校验异常
     * @return Result对象
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, "参数校验失败: " + message);
    }

    /**
     * 处理参数校验异常（@ModelAttribute参数校验）
     * 
     * @param e 参数校验异常
     * @return Result对象
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, "参数校验失败: " + message);
    }

    /**
     * 处理参数校验异常（@RequestParam参数校验）
     * 
     * @param e 参数校验异常
     * @return Result对象
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, "参数校验失败: " + message);
    }

    /**
     * 处理缺少必需的请求参数异常（@RequestParam 参数缺失）
     * 
     * @param e 缺少请求参数异常
     * @return Result对象
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String parameterName = e.getParameterName();
        String parameterType = e.getParameterType();
        log.warn("缺少必需的请求参数: {} (类型: {})", parameterName, parameterType);
        return Result.error(400, String.format("缺少必需的请求参数: %s", parameterName));
    }

    /**
     * 处理请求参数类型不匹配异常（如将字符串传给需要数字的参数）
     * 
     * @param e 参数类型不匹配异常
     * @return Result对象
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String parameterName = e.getName();
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型";
        Object value = e.getValue();
        log.warn("请求参数类型不匹配: {} = {} (期望类型: {})", parameterName, value, requiredType);
        return Result.error(400, String.format("请求参数类型错误: %s 应为 %s 类型", parameterName, requiredType));
    }

    /**
     * 处理JSON解析异常（如日期格式错误、类型不匹配、请求体为空等）
     * 
     * @param e JSON解析异常
     * @return Result对象
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        String message = e.getMessage();
        // 提取更友好的错误信息
        if (message != null) {
            // 处理请求体为空的情况
            if (message.contains("Required request body is missing")
                    || message.contains("I/O error while reading input message")
                    || message.contains("Required request body")) {
                log.warn("请求体为空");
                return Result.error(400, "请求体不能为空，请提供有效的JSON数据");
            }
            // 处理日期格式错误
            if (message.contains("LocalDate") || message.contains("LocalDateTime")) {
                log.warn("日期格式错误: {}", message);
                return Result.error(400, "日期格式错误，请使用 yyyy-MM-dd 格式（例如：2024-09-01）");
            }
            // 处理JSON反序列化失败
            if (message.contains("Cannot deserialize")) {
                log.warn("JSON反序列化失败: {}", message);
                return Result.error(400, "请求参数格式错误，请检查数据类型是否正确");
            }
            // 处理JSON格式错误
            if (message.contains("JSON parse error") || message.contains("Unexpected character")) {
                log.warn("JSON格式错误: {}", message);
                return Result.error(400, "JSON格式错误，请检查请求体格式是否正确");
            }
        }
        log.warn("JSON解析失败: {}", message);
        return Result.error(400, "请求参数格式错误: " + (message != null ? message : "未知错误"));
    }

    /**
     * 处理非法参数异常
     * 
     * @param e 非法参数异常
     * @return Result对象
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, "非法参数: " + e.getMessage());
    }

    /**
     * 处理运行时异常
     * 
     * @param e 运行时异常
     * @return Result对象
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        return Result.error(500, "系统内部错误: " + e.getMessage());
    }

    /**
     * 处理所有其他异常
     * 
     * @param e 异常
     * @return Result对象
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(500, "系统异常，请联系管理员");
    }
}
