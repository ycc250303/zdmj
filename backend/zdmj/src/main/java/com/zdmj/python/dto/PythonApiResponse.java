package com.zdmj.python.dto;

import lombok.Data;

/**
 * Python API响应基类
 * 所有Python服务返回的响应DTO都应继承此类
 * 
 * 注意：Python服务返回的响应格式应该与Java的Result格式保持一致
 * 或者Python服务返回标准格式，Java端进行适配
 */
@Data
public class PythonApiResponse<T> {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 判断是否成功
     * 
     * @return true表示成功，false表示失败
     */
    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
