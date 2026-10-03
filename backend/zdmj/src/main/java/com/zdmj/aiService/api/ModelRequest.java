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
}
