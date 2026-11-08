package com.zdmj.aiService.service;

import java.util.List;

import com.zdmj.aiService.dto.LlmModelOptionResponse;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConnectionTestRequest;

/** 用户模型配置管理，由 AI 模块的 HTTP 入口调用。 */
public interface UserLlmConfigService {

    /** @return 当前用户配置，没有记录时 configured 为 false；密钥仅返回掩码 */
    UserLlmConfigResponse getMyConfig();

    /** @return 模型目录中可供用户选择的编号与展示名称 */
    List<LlmModelOptionResponse> listModels();

    /**
     * 校验模型、加密密钥并保存当前用户配置，然后清除客户端缓存。
     *
     * @param request 模型编号与供应商密钥
     * @throws com.zdmj.common.exception.BusinessException 未登录、模型非法或密钥加密失败
     */
    void saveMyConfig(UserLlmConfigRequest request);

    /** 删除当前用户配置并清除客户端缓存；没有配置时仍然成功。 */
    void deleteMyConfig();

    /**
     * 用请求中的模型与密钥发起探测，不读取或保存用户配置。
     *
     * @param request 模型编号与探测密钥
     * @throws com.zdmj.common.exception.BusinessException 模型非法或供应商探测失败
     */
    void testConnection(UserLlmConnectionTestRequest request);
}
