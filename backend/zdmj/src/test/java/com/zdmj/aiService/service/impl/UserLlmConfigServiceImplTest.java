package com.zdmj.aiService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.dto.UserLlmConfigRequest;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;
import com.zdmj.aiService.support.ResolvedModelConfiguration;
import com.zdmj.aiService.support.UserApiKeyCipher;
import com.zdmj.aiService.support.UserModelConfigurationReader;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class UserLlmConfigServiceImplTest {

    @Mock
    private UserLlmConfigMapper mapper;
    @Mock
    private ModelGatewayImpl gateway;
    @Mock
    private UserApiKeyCipher cipher;
    @Mock
    private ModelClientConfiguration clients;
    @Mock
    private ChatModel probe;
    @Mock
    private UserModelConfigurationReader reader;

    private UserLlmConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserLlmConfigServiceImpl(mapper, gateway, cipher, clients, reader,
                Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void getMyConfig_shouldUseSharedReaderAndOnlyExposeMaskedKey() {
        CurrentActor actor = CurrentActor.of(7L);
        when(reader.find(actor)).thenReturn(Optional.of(
                new ResolvedModelConfiguration(ModelCode.DEEPSEEK_FLASH, "sk-secret-key")));

        var response = service.getMyConfig(actor);

        assertEquals(true, response.isConfigured());
        assertEquals("deepseek-flash", response.getModelCode());
        assertEquals(ModelCode.DEEPSEEK_FLASH.displayName(), response.getModelDisplayName());
        assertEquals(UserApiKeyCipher.mask("sk-secret-key"), response.getApiKeyMasked());
        assertEquals(java.util.Arrays.stream(ModelCode.values()).map(ModelCode::code).toList(),
                response.getModels().stream().map(option -> option.code()).toList());
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void getMyConfig_whenNoRecord_shouldReturnUnconfigured() {
        when(reader.find(CurrentActor.of(7L))).thenReturn(Optional.empty());

        var response = service.getMyConfig(CurrentActor.of(7L));

        assertEquals(false, response.isConfigured());
        assertEquals(ModelCode.values().length, response.getModels().size());
        org.junit.jupiter.api.Assertions.assertNull(response.getApiKeyMasked());
        org.junit.jupiter.api.Assertions.assertNull(response.getModelCode());
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void writes_whenActorMissing_shouldRejectBeforeAccessingStorage() {
        for (CurrentActor actor : new CurrentActor[]{null, CurrentActor.of(null)}) {
            assertEquals(ErrorCode.USER_NOT_LOGIN, assertThrows(BusinessException.class,
                    () -> service.saveMyConfig(actor, request("deepseek-flash", "secret"))).getErrorCode());
            assertEquals(ErrorCode.USER_NOT_LOGIN, assertThrows(BusinessException.class,
                    () -> service.deleteMyConfig(actor)).getErrorCode());
        }
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void save_whenWriteCountInvalid_shouldExposeImplementationFailureAndNotEvict() {
        when(clients.buildProbe(ModelCode.DEEPSEEK_FLASH, "secret")).thenReturn(probe);
        when(probe.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("pong")))));
        when(cipher.encrypt("secret")).thenReturn("ciphertext");
        when(mapper.upsert(any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.saveMyConfig(CurrentActor.of(7L), request(" DEEPSEEK-V4-FLASH ", " secret ")));

        verify(mapper).upsert(org.mockito.ArgumentMatchers.argThat(config ->
                config.getUserId().equals(7L) && config.getModelCode().equals("deepseek-flash")
                        && config.getApiKeyCiphertext().equals("ciphertext")
                        && config.getCreatedAt().equals(LocalDateTime.of(2026, 11, 9, 0, 0))
                        && config.getUpdatedAt().equals(config.getCreatedAt())));
        verifyNoInteractions(gateway);
    }

    @Test
    void save_whenModelCodeInvalid_shouldThrowBeforeBuildingProbe() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveMyConfig(CurrentActor.of(7L), request("not-a-model", "key")));

        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID, exception.getErrorCode());
        verify(clients, never()).buildProbe(any(), any());
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void save_whenProbeFails_shouldPreserveCauseAndReturnExistingError() {
        IllegalStateException failure = new IllegalStateException("probe unavailable");
        when(clients.buildProbe(ModelCode.DEEPSEEK_FLASH, "key")).thenReturn(probe);
        when(probe.call(any(Prompt.class))).thenThrow(failure);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveMyConfig(CurrentActor.of(7L), request("deepseek-flash", "key")));

        assertEquals(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, exception.getErrorCode());
        assertSame(failure, exception.getCause());
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void save_whenBusinessFailure_shouldPropagateOriginalException() {
        BusinessException failure = new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
        when(clients.buildProbe(ModelCode.DEEPSEEK_FLASH, "key")).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> service.saveMyConfig(CurrentActor.of(7L), request("deepseek-flash", "key"))));
        verifyNoInteractions(mapper, gateway, cipher);
    }

    private static UserLlmConfigRequest request(String modelCode, String apiKey) {
        UserLlmConfigRequest request = new UserLlmConfigRequest();
        request.setModelCode(modelCode);
        request.setApiKey(apiKey);
        return request;
    }
}
