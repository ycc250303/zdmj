package com.zdmj.userAuthService.service;

import java.util.Map;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.llm.ModelEnum;

import reactor.core.publisher.Flux;

/**
 * 按用户配置调用对话模型。实现读取用户模型配置，调用方不访问配置表。
 */
public interface UserModelChat {

    /**
     * 按用户路由模型完成一次对话。
     *
     * @param userId      发起用户，为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param userMessage 用户消息
     * @param promptName  classpath 提示词名称，空则不附加系统提示词
     * @param promptVars  提示词占位符，空则不替换
     * @return 模型文本
     * @throws BusinessException 用户未登录，或用户模型配置不可用
     */
    String chatOnce(Long userId, String userMessage, String promptName, Map<String, Object> promptVars);

    /**
     * 按用户路由模型完成一次结构化对话。解析失败直接抛出异常。
     *
     * @param userId      发起用户，为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param userMessage 用户消息
     * @param promptName  classpath 提示词名称
     * @param promptVars  提示词占位符，空则不替换
     * @param outputType  目标类型
     * @param <T>         结构化结果类型
     * @return 解析后的对象
     * @throws BusinessException    用户未登录，或用户模型配置不可用
     * @throws IllegalStateException 模型输出无法解析为 {@code outputType}
     */
    <T> T chatStructuredOnce(Long userId, String userMessage, String promptName, Map<String, Object> promptVars,
            Class<T> outputType);

    /**
     * 使用平台指定模型完成一次结构化对话，忽略用户自配模型。
     *
     * @param userMessage 用户消息
     * @param promptName  classpath 提示词名称
     * @param promptVars  提示词占位符，空则不替换
     * @param outputType  目标类型
     * @param model       平台模型
     * @param <T>         结构化结果类型
     * @return 解析后的对象
     * @throws IllegalStateException 模型输出无法解析为 {@code outputType}
     */
    <T> T chatStructuredOnceWithPlatformModel(String userMessage, String promptName, Map<String, Object> promptVars,
            Class<T> outputType, ModelEnum model);

    /**
     * 在指定会话中按用户路由模型流式输出。
     *
     * @param userId         发起用户，为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param conversationId 会话标识，为空时抛出 {@link IllegalArgumentException}
     * @param userMessage    用户消息
     * @param promptName     classpath 提示词名称
     * @param promptVars     提示词占位符，空则不替换
     * @return 文本流
     * @throws BusinessException        用户未登录，或用户模型配置不可用
     * @throws IllegalArgumentException {@code conversationId} 为空
     */
    Flux<String> chatStreamInConversation(Long userId, Long conversationId, String userMessage, String promptName,
            Map<String, Object> promptVars);

    /**
     * 解析简历识别使用的平台模型。
     *
     * @return 平台简历识别模型
     */
    ModelEnum resolveResumeImportModel();
}
