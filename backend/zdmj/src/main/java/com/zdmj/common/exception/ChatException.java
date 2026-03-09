package com.zdmj.common.exception;

/**
 * Chat 相关异常
 */
public class ChatException extends RuntimeException {

    public ChatException(String message) {
        super(message);
    }

    public ChatException(String message, Throwable cause) {
        super(message, cause);
    }
}