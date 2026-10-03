package com.zdmj.userAuthService.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.aiService.model.ModelCode;
import com.zdmj.aiService.provider.ModelPurpose;
import com.zdmj.aiService.provider.ResolvedModelConfiguration;
import com.zdmj.aiService.provider.UserModelConfigurationProvider;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.entity.UserLlmConfig;
import com.zdmj.userAuthService.llm.UserApiKeyCipher;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

/**
 * 从用户模型配置表解析已保存的模型与密钥。平台密钥和供应商客户端不在本类处理。
 */
@Service
public class UserModelConfigurationProviderImpl implements UserModelConfigurationProvider {

    private final UserLlmConfigMapper userLlmConfigMapper;
    private final UserApiKeyCipher userApiKeyCipher;

    /**
     * @param userLlmConfigMapper 用户模型配置表
     * @param userApiKeyCipher    用户密钥加解密
     */
    public UserModelConfigurationProviderImpl(UserLlmConfigMapper userLlmConfigMapper,
            UserApiKeyCipher userApiKeyCipher) {
        this.userLlmConfigMapper = userLlmConfigMapper;
        this.userApiKeyCipher = userApiKeyCipher;
    }

    /**
     * 用户对话读取配置表并解密密钥。平台任务直接返回空配置。
     *
     * @param actor   当前操作者
     * @param purpose 解析用途
     * @return 用户配置；没有记录或用途为平台任务时返回空配置
     * @throws BusinessException 密文无法解密出密钥时抛出 {@link ErrorCode#USER_LLM_CONFIG_INVALID}
     */
    @Override
    public ResolvedModelConfiguration resolve(CurrentActor actor, ModelPurpose purpose) {
        if (purpose != ModelPurpose.USER_CHAT || actor == null || actor.userId() == null) {
            return ResolvedModelConfiguration.absent();
        }
        UserLlmConfig config = userLlmConfigMapper.selectById(actor.userId());
        if (config == null) {
            return ResolvedModelConfiguration.absent();
        }
        ModelCode model = ModelCode.fromCode(config.getModelCode());
        String apiKey = userApiKeyCipher.decrypt(config.getApiKeyCiphertext());
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
        }
        return new ResolvedModelConfiguration(model, apiKey.trim());
    }
}
