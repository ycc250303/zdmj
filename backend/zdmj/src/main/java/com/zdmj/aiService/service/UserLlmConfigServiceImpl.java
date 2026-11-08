package com.zdmj.aiService.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.support.UserApiKeyCipher;
import com.zdmj.aiService.dto.LlmModelOptionResponse;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConnectionTestRequest;
import com.zdmj.aiService.entity.UserLlmConfig;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理当前用户的模型配置、密钥存储与连通性检查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLlmConfigServiceImpl implements UserLlmConfigService {
    private final UserLlmConfigMapper userLlmConfigMapper;
    private final ModelGatewayImpl modelGateway;
    private final UserApiKeyCipher userApiKeyCipher;
    private final ModelClientConfiguration clients;

    @Override
    public UserLlmConfigResponse getMyConfig(){
        Long userId = UserHolder.getUserId();
        UserLlmConfig config = userLlmConfigMapper.selectById(userId);
        if (config == null) {
            UserLlmConfigResponse dto = new UserLlmConfigResponse();
            dto.setConfigured(false);
            return dto;
        }

        String plain = userApiKeyCipher.decrypt(config.getApiKeyCiphertext());
        UserLlmConfigResponse dto = new UserLlmConfigResponse();
        ModelCode meta = ModelCode.fromCode(config.getModelCode());
        dto.setConfigured(true);
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
        String modelCode = request.getModelCode();
        String apiKey = request.getApiKey().trim();
        long startedAt = System.currentTimeMillis();
        try {
            ModelCode model = ModelCode.fromCode(modelCode);
            ChatClient.builder(clients.buildProbe(modelCode, apiKey))
                    .build()
                    .prompt()
                    .user("ping")
                    .call()
                    .content();
            log.info("[UserLlmConfig] testConnection ok modelCode={} apiModel={} elapsedMs={}",
                    modelCode, model.apiModelName(), System.currentTimeMillis() - startedAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UserLlmConfig] testConnection failed modelCode={} elapsedMs={}",
                    modelCode, System.currentTimeMillis() - startedAt, e);
            throw new BusinessException(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, e);
        }
    }
}
