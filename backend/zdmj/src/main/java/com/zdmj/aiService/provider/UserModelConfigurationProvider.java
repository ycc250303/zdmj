package com.zdmj.aiService.provider;

import com.zdmj.common.context.CurrentActor;

/**
 * 查询用户已保存的模型配置。实现位于用户认证模块，模型网关不访问用户表和配置表。
 */
public interface UserModelConfigurationProvider {

    /**
     * 按操作者解析用户模型配置。
     *
     * @param actor 当前操作者
     * @return 用户配置；没有可用配置时返回 {@link ResolvedModelConfiguration#absent()}
     */
    ResolvedModelConfiguration resolve(CurrentActor actor);
}
