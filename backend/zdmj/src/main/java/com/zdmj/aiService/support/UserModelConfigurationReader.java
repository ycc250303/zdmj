package com.zdmj.aiService.support;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.zdmj.aiService.entity.UserLlmConfig;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 从用户模型配置表读取、解析并解密配置，供配置展示与模型调用共同使用。
 */
@Component
public class UserModelConfigurationReader {

    private final UserLlmConfigMapper userLlmConfigMapper;
    private final UserApiKeyCipher userApiKeyCipher;

    /**
     * @param userLlmConfigMapper 用户模型配置表
     * @param userApiKeyCipher    用户密钥加解密
     */
    public UserModelConfigurationReader(UserLlmConfigMapper userLlmConfigMapper,
            UserApiKeyCipher userApiKeyCipher) {
        this.userLlmConfigMapper = userLlmConfigMapper;
        this.userApiKeyCipher = userApiKeyCipher;
    }

    /**
     * 读取当前用户配置并解密密钥。
     *
     * @param actor 当前操作者
     * @return 已解析的配置；仅没有记录时返回 empty
     * @throws BusinessException 身份缺失、模型非法或密钥无法解密出有效内容
     */
    public Optional<ResolvedModelConfiguration> find(CurrentActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        UserLlmConfig config = userLlmConfigMapper.selectById(actor.userId());
        if (config == null) {
            return Optional.empty();
        }
        ModelCode model = ModelCode.fromCode(config.getModelCode());
        String apiKey = userApiKeyCipher.decrypt(config.getApiKeyCiphertext());
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
        }
        return Optional.of(new ResolvedModelConfiguration(model, apiKey));
    }
}
