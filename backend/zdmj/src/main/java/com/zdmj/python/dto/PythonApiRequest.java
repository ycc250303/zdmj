package com.zdmj.python.dto;

import lombok.Data;

/**
 * Python API请求基类
 * 所有调用Python服务的请求DTO都应继承此类
 */
@Data
public class PythonApiRequest {
    // 基类，可根据需要添加通用字段
    // 例如：请求ID、时间戳、用户ID等
}
