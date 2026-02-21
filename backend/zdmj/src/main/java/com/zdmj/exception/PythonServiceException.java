package com.zdmj.exception;

import lombok.Getter;

/**
 * Python服务调用异常
 * 用于封装调用Python服务时发生的各种异常
 */
@Getter
public class PythonServiceException extends RuntimeException {

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * HTTP状态码
     */
    private final Integer httpStatus;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public PythonServiceException(String message) {
        super(message);
        this.code = 5001;
        this.httpStatus = 500;
        this.message = message;
    }

    /**
     * 构造函数
     * 
     * @param code    错误码
     * @param message 错误消息
     */
    public PythonServiceException(Integer code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = 500;
        this.message = message;
    }

    /**
     * 构造函数
     * 
     * @param code       错误码
     * @param httpStatus HTTP状态码
     * @param message    错误消息
     */
    public PythonServiceException(Integer code, Integer httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 构造函数
     * 
     * @param message 错误消息
     * @param cause   异常原因
     */
    public PythonServiceException(String message, Throwable cause) {
        super(message, cause);
        this.code = 5001;
        this.httpStatus = 500;
        this.message = message;
    }

    /**
     * 构造函数
     * 
     * @param code    错误码
     * @param message 错误消息
     * @param cause   异常原因
     */
    public PythonServiceException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = 500;
        this.message = message;
    }

    /**
     * 构造函数
     * 
     * @param code       错误码
     * @param httpStatus HTTP状态码
     * @param message    错误消息
     * @param cause      异常原因
     */
    public PythonServiceException(Integer code, Integer httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
