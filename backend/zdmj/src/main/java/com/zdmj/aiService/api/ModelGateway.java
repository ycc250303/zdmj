package com.zdmj.aiService.api;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import reactor.core.publisher.Flux;

/**
 * 模型调用入口。业务模块通过本接口完成文本、结构化和流式调用，不访问用户模型配置表。
 */
public interface ModelGateway {

    /**
     * 按用户配置完成一次文本生成。
     *
     * @param actor   当前操作者，用户主键为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param request 生成请求
     * @return 模型文本
     * @throws BusinessException 用户未登录，或模型配置不可用
     */
    String generate(CurrentActor actor, ModelRequest request);

    /**
     * 按用户配置完成一次结构化生成。
     *
     * @param actor   当前操作者，用户主键为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param request 结构化请求
     * @param <T>     结果类型
     * @return 解析后的对象
     * @throws BusinessException     用户未登录，或模型配置不可用
     * @throws IllegalStateException 模型输出无法解析为目标类型
     */
    <T> T generateStructured(CurrentActor actor, StructuredModelRequest<T> request);

    /**
     * 在指定会话中按用户配置流式输出。调用方传入会话标识后，网关绑定会话记忆。
     *
     * @param actor   当前操作者，用户主键为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param request 流式请求，会话标识为空时抛出 {@link IllegalArgumentException}
     * @return 文本流
     * @throws BusinessException        用户未登录，或模型配置不可用
     * @throws IllegalArgumentException 会话标识为空
     */
    Flux<String> stream(CurrentActor actor, StreamingModelRequest request);
}
