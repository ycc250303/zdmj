package com.zdmj.aiService.provider;

import com.zdmj.common.context.CurrentActor;

/**
 * 查询用户已保存的模型配置。实现位于用户认证模块，模型网关不访问用户表和配置表。
 */
public interface UserModelConfigurationProvider {

    /**
     * 按操作者与用途解析用户模型配置。
     *
     * @param actor   当前操作者
     * @param purpose 解析用途；平台任务不读取用户配置
     * @return 用户配置；没有可用配置时返回 {@link ResolvedModelConfiguration#absent()}
     */
    ResolvedModelConfiguration resolve(CurrentActor actor, ModelPurpose purpose);
}
