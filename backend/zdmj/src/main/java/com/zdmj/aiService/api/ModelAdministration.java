package com.zdmj.aiService.api;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 用户对话客户端的管理入口。保存或删除模型配置后清除缓存，连通性探测不读库、不写缓存。
 */
public interface ModelAdministration {

    /**
     * 清除指定用户的客户端缓存。保存或删除用户模型配置后调用。
     *
     * @param actor 当前操作者，用户主键为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @throws BusinessException 用户未登录
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
