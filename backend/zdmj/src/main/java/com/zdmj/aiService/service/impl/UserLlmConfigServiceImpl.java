package com.zdmj.aiService.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.aiService.entity.UserLlmConfig;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;
import com.zdmj.aiService.service.UserLlmConfigService;
import com.zdmj.aiService.support.ModelClientFactory;
import com.zdmj.aiService.support.UserApiKeyCipher;
import com.zdmj.aiService.support.UserModelConfigurationReader;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

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
    private final UserApiKeyCipher userApiKeyCipher;
    private final ModelClientFactory clients;
    private final UserModelConfigurationReader configurationReader;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /**
     * 获取当前用户的模型配置。
     *
     * @param actor 当前操作者
     * @return 模型配置
     */
    @Override
    public UserLlmConfigResponse getMyConfig(CurrentActor actor) {
        UserLlmConfigResponse dto = new UserLlmConfigResponse();
        dto.setModels(Arrays.stream(ModelCode.values())
                .map(model -> new UserLlmConfigResponse.ModelOption(model.code(), model.displayName())).toList());
        configurationReader.find(actor).ifPresent(config -> {
            dto.setConfigured(true);
            dto.setModelCode(config.model().code());
            dto.setModelDisplayName(config.model().displayName());
            dto.setApiKeyMasked(UserApiKeyCipher.mask(config.apiKey()));
        });
        return dto;
    }

    /**
     * 保存当前用户的模型配置。
     *
     * @param actor 当前操作者
     * @param request 模型配置请求
     */
    @Override
    public void saveMyConfig(CurrentActor actor, UserLlmConfigRequest request) {
        Long userId = requireUserId(actor);
        ModelCode model = ModelCode.fromCode(request.getModelCode());
        String apiKey = request.getApiKey().trim();
        // 挂起调用方事务，供应商探测完成后再进入配置写入事务。
        TransactionTemplate probeTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        probeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        probeTransaction.executeWithoutResult(status -> testConnection(model, apiKey));
        String ciphertext = userApiKeyCipher.encrypt(apiKey);
        UserLlmConfig config = new UserLlmConfig();
        config.setUserId(userId);
        config.setModelCode(model.code());
        config.setApiKeyCiphertext(ciphertext);
        LocalDateTime now = LocalDateTime.now(clock);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        transactionTemplate.executeWithoutResult(status -> {
            if (userLlmConfigMapper.upsert(config) != 1) {
                throw new IllegalStateException("保存模型配置必须影响一条记录");
            }
        });
    }

    /**
     * 删除当前用户的模型配置。
     *
     * @param actor 当前操作者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyConfig(CurrentActor actor) {
        Long userId = requireUserId(actor);
        int deleted = userLlmConfigMapper.deleteById(userId);
        if (deleted < 0 || deleted > 1) {
            throw new IllegalStateException("删除模型配置最多影响一条记录");
        }
    }

    /**
     * 使用已校验的请求凭据探测供应商，失败立即终止保存
     *
     * @param model 模型目录项
     * @param apiKey 明文密钥
     */
    private void testConnection(ModelCode model, String apiKey) {
        long startedAt = System.currentTimeMillis();
        ChatClient client = ChatClient.builder(clients.buildProbe(model, apiKey)).build();
        try {
            client.prompt()
                    .user("ping")
                    .call()
                    .content();
            log.info("[UserLlmConfig] testConnection ok modelCode={} apiModel={} elapsedMs={}",
                    model.code(), model.apiModelName(), System.currentTimeMillis() - startedAt);
        } catch (RestClientException | TransientAiException | NonTransientAiException e) {
            log.warn("[UserLlmConfig] testConnection failed modelCode={} elapsedMs={}",
                    model.code(), System.currentTimeMillis() - startedAt, e);
            throw new BusinessException(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, e);
        }
    }

    private static Long requireUserId(CurrentActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return actor.userId();
    }
}
