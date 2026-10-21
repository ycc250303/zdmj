package com.zdmj.aiService.api;

import java.util.Map;

/**
 * 单次文本生成请求。
 *
 * @param message    用户消息
 * @param promptName classpath 提示词名称，空则不附加系统提示词
 * @param promptVars 提示词占位符，空则不替换
 */
public record ModelRequest(String message, String promptName, Map<String, Object> promptVars) {
    /** 复制变量映射，保留空值并禁止修改映射结构；变量对象由调用方保证稳定。 */
    public ModelRequest {
        promptVars = promptVars == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(promptVars));
    }
}
