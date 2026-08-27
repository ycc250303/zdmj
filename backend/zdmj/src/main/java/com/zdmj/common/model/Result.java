package com.zdmj.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装类
 * 
 * @param <T> 返回数据的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 响应码（0 成功，非 0 为业务错误码）
     */
    private Integer code;

    /**
     * 消息提示
     */
    private String msg;

    /**
     * 返回数据
     */
    private T data;

    /**
     * 成功响应（无数据）
     * 
     * @return Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(0, "操作成功", null);
    }

    /**
     * 成功响应（带数据）
     * 
     * @param data 返回数据
     * @return Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "操作成功", data);
    }

    /**
     * 成功响应（自定义消息和数据）
     * 
     * @param msg  消息提示
     * @param data 返回数据
     * @return Result对象
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(0, msg, data);
    }
}
