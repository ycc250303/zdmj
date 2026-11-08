package com.zdmj.aiService.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

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

import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.dto.UserLlmConnectionTestRequest;
import com.zdmj.aiService.mapper.UserLlmConfigMapper;
import com.zdmj.aiService.support.UserApiKeyCipher;
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

    private UserLlmConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserLlmConfigServiceImpl(mapper, gateway, cipher, clients);
    }

    @Test
    void testConnection_whenModelCodeInvalid_shouldThrowBeforeBuildingProbe() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.testConnection(request("not-a-model", "key")));

        assertEquals(ErrorCode.USER_LLM_CONFIG_INVALID, exception.getErrorCode());
        verify(clients, never()).buildProbe(any(), any());
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void testConnection_whenSuccessful_shouldUseRequestKeyWithoutChangingSavedConfig() {
        when(clients.buildProbe("deepseek-flash", "probe-key")).thenReturn(probe);
        when(probe.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("pong")))));

        service.testConnection(request("deepseek-flash", "  probe-key  "));

        verify(probe).call(org.mockito.ArgumentMatchers.argThat(
                (Prompt prompt) -> "ping".equals(prompt.getUserMessage().getText())));
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void testConnection_whenProbeFails_shouldPreserveCauseAndReturnExistingError() {
        IllegalStateException failure = new IllegalStateException("probe unavailable");
        when(clients.buildProbe("deepseek-flash", "key")).thenReturn(probe);
        when(probe.call(any(Prompt.class))).thenThrow(failure);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.testConnection(request("deepseek-flash", "key")));

        assertEquals(ErrorCode.USER_LLM_CONNECTION_TEST_FAILED, exception.getErrorCode());
        assertSame(failure, exception.getCause());
        verifyNoInteractions(mapper, gateway, cipher);
    }

    @Test
    void testConnection_whenBusinessFailure_shouldPropagateOriginalException() {
        BusinessException failure = new BusinessException(ErrorCode.USER_LLM_CONFIG_INVALID);
        when(clients.buildProbe("deepseek-flash", "key")).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> service.testConnection(request("deepseek-flash", "key"))));
        verifyNoInteractions(mapper, gateway, cipher);
    }

    private static UserLlmConnectionTestRequest request(String modelCode, String apiKey) {
        UserLlmConnectionTestRequest request = new UserLlmConnectionTestRequest();
        request.setModelCode(modelCode);
        request.setApiKey(apiKey);
        return request;
    }
}
