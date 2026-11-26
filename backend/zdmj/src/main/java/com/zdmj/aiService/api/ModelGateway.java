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
     * @param request 模型输入
     * @param outputType 非空的结构化结果类型
     * @param <T>     结果类型
     * @return 解析后的对象
     * @throws BusinessException     用户未登录，或模型配置不可用
     * @throws IllegalStateException 模型输出无法解析为目标类型
     * @throws IllegalArgumentException 结构化结果类型为空
     */
    <T> T generateStructured(CurrentActor actor, ModelRequest request, Class<T> outputType);

    /**
     * 按用户配置流式生成，历史与完成情况由调用方显式提供，网关不持久化历史。
     *
     * @param actor 已认证操作者
     * @param request 包含当前问题、历史快照与系统提示词的输入
     * @return 增量文本流
     * @throws BusinessException 用户未登录或模型配置不可用
     */
    Flux<String> stream(CurrentActor actor, ModelRequest request);
}
