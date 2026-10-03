package com.zdmj.userAuthService.service.impl;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.model.ModelCode;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.context.UserHolder;
import com.zdmj.userAuthService.llm.UserApiKeyCipher;
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
    private final ModelGateway modelGateway;
    private final UserApiKeyCipher userApiKeyCipher;

    @Override
    public UserLlmConfigResponse getMyConfig(){
        Long userId = UserHolder.getUserId();
        UserLlmConfig config = userLlmConfigMapper.selectById(userId);
        if (config == null) {
            UserLlmConfigResponse dto = new UserLlmConfigResponse();
            dto.setConfigured(false);
            dto.setUsingPlatformDefault(modelGateway.platformFallbackEnabled());
            return dto;
        }

        String plain = userApiKeyCipher.decrypt(config.getApiKeyCiphertext());
        UserLlmConfigResponse dto = new UserLlmConfigResponse();
        ModelCode meta = ModelCode.fromCode(config.getModelCode());
        dto.setConfigured(true);
        dto.setUsingPlatformDefault(false);
        dto.setModelCode(meta.code());
        dto.setModelDisplayName(meta.displayName());
        dto.setApiKeyMasked(UserApiKeyCipher.mask(plain));
        return dto;
    }

    @Override
    public List<LlmModelOptionResponse> listModels(){
        return Arrays.stream(ModelCode.values())
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
        ModelCode.fromCode(request.getModelCode());
        String ciphertext = userApiKeyCipher.encrypt(request.getApiKey().trim());
        
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
        modelGateway.evict(CurrentActor.of(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyConfig(){
        Long userId = UserHolder.requireUserId();
        userLlmConfigMapper.deleteById(userId);
        modelGateway.evict(CurrentActor.of(userId));
    }

    @Override
    public void testConnection(UserLlmConnectionTestRequest request) {
        modelGateway.testConnection(request.getModelCode(), request.getApiKey().trim());
    }
}
