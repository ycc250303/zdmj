package com.zdmj.aiService.service;

import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.common.context.CurrentActor;

/** 用户模型配置管理，由 AI 模块的 HTTP 入口调用。 */
public interface UserLlmConfigService {

    /** @return 当前用户配置及可选模型；没有记录时 configured 为 false，密钥仅返回掩码 */
    UserLlmConfigResponse getMyConfig(CurrentActor actor);

    /**
     * 在数据库事务外探测模型与密钥，成功后加密并完整替换当前用户配置。
     * 探测失败时保留原配置；并发写入按数据库执行顺序覆盖。
     * 创建时间保持不变，更新时间来自应用时钟，事务提交后清除两类客户端缓存。
     *
     * @param actor 当前操作者
     * @param request 模型编号与供应商密钥
     * @throws com.zdmj.common.exception.BusinessException 未登录、模型非法、探测失败或密钥加密失败
     */
    void saveMyConfig(CurrentActor actor, UserLlmConfigRequest request);

    /** 删除当前用户配置，事务提交后清除两类客户端缓存；没有配置时仍然成功。 */
    void deleteMyConfig(CurrentActor actor);
}
