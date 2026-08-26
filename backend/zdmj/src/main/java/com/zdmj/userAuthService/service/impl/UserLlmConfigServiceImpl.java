package com.zdmj.userAuthService.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zdmj.common.ai.ModelEnum;
import com.zdmj.common.ai.UserLlmRouter;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.UserApiKeyCipher;
import com.zdmj.userAuthService.dto.LlmModelOptionResponse;
import com.zdmj.userAuthService.dto.UserLlmConfigResponse;
import com.zdmj.userAuthService.dto.UserLlmConfigRequest;
import com.zdmj.userAuthService.dto.UserLlmConnectionTestRequest;
import com.zdmj.userAuthService.entity.UserLlmConfig;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;
import com.zdmj.userAuthService.service.UserLlmConfigService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserLlmConfigServiceImpl implements UserLlmConfigService {
    private final UserLlmConfigMapper userLlmConfigMapper;
    private final UserLlmRouter userLlmRouter;

    @Override
    public UserLlmConfigResponse getMyConfig(){
        Long userId = UserHolder.getUserId();
        UserLlmConfig config = userLlmConfigMapper.selectById(userId);
        if (config == null) {
            UserLlmConfigResponse dto = new UserLlmConfigResponse();
            dto.setConfigured(false);
            dto.setUsingPlatformDefault(userLlmRouter.isPlatformFallbackEnabled());
            return dto;
        }

        String plain = userLlmRouter.decryptApiKey(config.getApiKeyCiphertext());
        UserLlmConfigResponse dto = new UserLlmConfigResponse();
        dto.setConfigured(true);
        dto.setUsingPlatformDefault(false);
        dto.setModelCode(config.getModelCode());
        dto.setModelDisplayName(ModelEnum.fromCode(config.getModelCode()).displayName());
        dto.setApiKeyMasked(UserApiKeyCipher.mask(plain));
        return dto;
    }

    @Override
    public List<LlmModelOptionResponse> listModels(){
        return userLlmRouter.listModelOptions().stream()
        .map(v -> {
            LlmModelOptionResponse dto = new LlmModelOptionResponse();
            dto.setCode(v.code());
            dto.setDisplayName(v.displayName());
            return dto;
        })
        .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMyConfig(UserLlmConfigRequest request){
        Long userId = UserHolder.requireUserId();
        userLlmRouter.validateModelCode(request.getModelCode());
        String ciphertext = userLlmRouter.encryptApiKey(request.getApiKey().trim());
        
        UserLlmConfig existingConfig = userLlmConfigMapper.selectById(userId);
        if(existingConfig == null){
            UserLlmConfig newConfig = new UserLlmConfig();
            newConfig.setUserId(userId);
            newConfig.setModelCode(request.getModelCode());
            newConfig.setApiKeyCiphertext(ciphertext);
            userLlmConfigMapper.insert(newConfig);
        } else {
            existingConfig.setModelCode(request.getModelCode());
            existingConfig.setApiKeyCiphertext(ciphertext);
            userLlmConfigMapper.updateById(existingConfig);
        }
        userLlmRouter.evict(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyConfig(){
        Long userId = UserHolder.requireUserId();
        userLlmConfigMapper.deleteById(userId);
        userLlmRouter.evict(userId);
    }

    @Override
    public void testConnection(UserLlmConnectionTestRequest request) {
        userLlmRouter.validateModelCode(request.getModelCode());
        userLlmRouter.testConnection(request.getModelCode(), request.getApiKey().trim());
    }
}
