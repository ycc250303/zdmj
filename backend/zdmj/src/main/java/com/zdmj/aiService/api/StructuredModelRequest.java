package com.zdmj.aiService.api;

import java.util.Map;

/**
 * 单次结构化生成请求。按当前用户已保存的模型与密钥调用。
 *
 * @param message    用户消息
 * @param promptName classpath 提示词名称，空则不附加系统提示词
 * @param promptVars 提示词占位符，空则不替换
 * @param outputType 目标类型
 * @param <T>        结构化结果类型
 */
public record StructuredModelRequest<T>(
        String message,
        String promptName,
        Map<String, Object> promptVars,
        Class<T> outputType) {

    /** 复制变量映射，保留空值并禁止修改映射结构；变量对象由调用方保证稳定。 */
    public StructuredModelRequest {
        promptVars = promptVars == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(promptVars));
    }

    /**
     * 按用户配置构造结构化请求。
     *
     * @param message    用户消息
     * @param promptName classpath 提示词名称
     * @param promptVars 提示词占位符，空则不替换
     * @param outputType 目标类型
     * @param <T>        结构化结果类型
     * @return 请求
     */
    public static <T> StructuredModelRequest<T> of(String message, String promptName,
            Map<String, Object> promptVars, Class<T> outputType) {
        return new StructuredModelRequest<>(message, promptName, promptVars, outputType);
    }
}
