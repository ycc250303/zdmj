package com.zdmj.aiService.provider;

import com.zdmj.common.context.CurrentActor;

/**
 * AI 模块内部查询用户已保存的模型配置，供模型网关解析调用所需的模型与密钥。
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
