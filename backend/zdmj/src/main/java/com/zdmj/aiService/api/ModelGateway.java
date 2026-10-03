package com.zdmj.aiService.api;

import java.util.List;

import com.zdmj.aiService.model.ModelCode;
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
     * 完成一次结构化生成。{@code request.platformModel()} 有值时使用平台模型，否则按用户配置路由。
     *
     * @param actor   当前操作者；按用户配置路由时用户主键为空则抛出 {@link ErrorCode#USER_NOT_LOGIN}
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

    /**
     * 解析简历识别使用的平台模型：优先 DeepSeek Flash，未配置 DeepSeek 密钥时使用通义 Flash。
     *
     * @return 平台模型
     * @throws BusinessException 平台密钥都未配置时抛出 {@link ErrorCode#USER_LLM_NOT_CONFIGURED}
     */
    ModelCode resumeImportModel();

    /**
     * 列出可选模型目录。
     *
     * @return 目录项，顺序与 {@link ModelCode} 声明一致
     */
    List<ModelCode> listModels();

    /**
     * 当前环境是否允许未配置用户使用平台默认模型。
     *
     * @return 允许时为 true
     */
    boolean platformFallbackEnabled();

    /**
     * 清除指定用户的客户端缓存。保存或删除用户模型配置后调用。
     *
     * @param actor 当前操作者
     */
    void evict(CurrentActor actor);

    /**
     * 用请求中的模型码和明文密钥探测连通性，不读库、不写缓存。
     *
     * @param modelCode 模型码
     * @param apiKey    明文密钥
     * @throws BusinessException 模型码非法，或探测失败
     */
    void testConnection(String modelCode, String apiKey);
}
