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
     * 处理JSON解析异常（如日期格式错误、类型不匹配等）
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
            if (message.contains("LocalDate") || message.contains("LocalDateTime")) {
                log.warn("日期格式错误: {}", message);
                return Result.error(400, "日期格式错误，请使用 yyyy-MM-dd 格式（例如：2024-09-01）");
            } else if (message.contains("Cannot deserialize")) {
                log.warn("JSON反序列化失败: {}", message);
                return Result.error(400, "请求参数格式错误，请检查数据类型是否正确");
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
