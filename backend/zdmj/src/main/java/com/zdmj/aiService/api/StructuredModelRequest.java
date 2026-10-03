package com.zdmj.aiService.api;

import java.util.Map;

import com.zdmj.aiService.model.ModelCode;

/**
 * 单次结构化生成请求。
 *
 * @param message       用户消息
 * @param promptName    classpath 提示词名称，空则不附加系统提示词
 * @param promptVars    提示词占位符，空则不替换
 * @param outputType    目标类型
 * @param platformModel 指定时忽略用户配置，使用该平台模型；为空时按用户配置路由
 * @param <T>           结构化结果类型
 */
public record StructuredModelRequest<T>(
        String message,
        String promptName,
        Map<String, Object> promptVars,
        Class<T> outputType,
        ModelCode platformModel) {

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
        return new StructuredModelRequest<>(message, promptName, promptVars, outputType, null);
    }

    /**
     * 构造使用指定平台模型的结构化请求，忽略用户自配模型。
     *
     * @param message    用户消息
     * @param promptName classpath 提示词名称
     * @param promptVars 提示词占位符，空则不替换
     * @param outputType 目标类型
     * @param model      平台模型
     * @param <T>        结构化结果类型
     * @return 请求
     */
    public static <T> StructuredModelRequest<T> platform(String message, String promptName,
            Map<String, Object> promptVars, Class<T> outputType, ModelCode model) {
        return new StructuredModelRequest<>(message, promptName, promptVars, outputType, model);
    }
}
