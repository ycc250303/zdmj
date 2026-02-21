package com.zdmj.python.constant;

import lombok.Getter;

/**
 * Python服务错误码定义
 * 用于统一管理Python服务调用过程中的错误码
 */
@Getter
public enum PythonErrorCode {

    /**
     * 成功
     */
    SUCCESS(200, "成功"),

    /**
     * Python服务连接超时
     */
    CONNECTION_TIMEOUT(5001, "Python服务连接超时"),

    /**
     * Python服务读取超时
     */
    READ_TIMEOUT(5002, "Python服务读取超时"),

    /**
     * Python服务不可用
     */
    SERVICE_UNAVAILABLE(5003, "Python服务不可用"),

    /**
     * Python服务返回错误响应
     */
    SERVICE_ERROR(5004, "Python服务返回错误响应"),

    /**
     * Python服务响应格式错误
     */
    INVALID_RESPONSE(5005, "Python服务响应格式错误"),

    /**
     * Python服务请求参数错误
     */
    INVALID_REQUEST(5006, "Python服务请求参数错误"),

    /**
     * Python服务未知错误
     */
    UNKNOWN_ERROR(5099, "Python服务未知错误");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造函数
     * 
     * @param code    错误码
     * @param message 错误消息
     */
    PythonErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
