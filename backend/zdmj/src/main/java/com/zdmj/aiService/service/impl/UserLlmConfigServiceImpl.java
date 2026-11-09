package com.zdmj.aiService.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.aiService.entity.UserLlmConfig;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;
import com.zdmj.aiService.service.UserLlmConfigService;
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
    private final ModelGatewayImpl modelGateway;
    private final UserApiKeyCipher userApiKeyCipher;
    private final ModelClientConfiguration clients;
    private final UserModelConfigurationReader configurationReader;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

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
            evictAfterCommit(actor);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyConfig(CurrentActor actor) {
        Long userId = requireUserId(actor);
        int deleted = userLlmConfigMapper.deleteById(userId);
        if (deleted < 0 || deleted > 1) {
            throw new IllegalStateException("删除模型配置最多影响一条记录");
        }
        evictAfterCommit(actor);
    }

    /** 使用已校验的请求凭据探测供应商，失败立即终止保存，保留原因与既有 2014 错误码。 */
    private void testConnection(ModelCode model, String apiKey) {
        long startedAt = System.currentTimeMillis();
        try {
            ChatClient.builder(clients.buildProbe(model, apiKey))
                    .build()
                    .prompt()
                    .user("ping")
                    .call()
                    .content();
            log.info("[UserLlmConfig] testConnection ok modelCode={} apiModel={} elapsedMs={}",
                    model.code(), model.apiModelName(), System.currentTimeMillis() - startedAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UserLlmConfig] testConnection failed modelCode={} elapsedMs={}",
                    model.code(), System.currentTimeMillis() - startedAt, e);
            throw new BusinessException(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, e);
        }
    }

    /**
     * 数据库提交后同步清除两类客户端；回滚不清除。清除异常传播给调用方，已提交数据保留。
     * 注册时要求事务同步已经启用，避免写入在缺少事务代理时被误认为成功。
     */
    private void evictAfterCommit(CurrentActor actor) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                modelGateway.evict(actor);
            }
        });
    }

    private static Long requireUserId(CurrentActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return actor.userId();
    }
}
