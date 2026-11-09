package com.zdmj.aiService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.support.UserModelConfigurationReader;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.prompt.PromptLoader;

/**
 * 用户未保存模型配置时，对话与结构化调用立即返回 2010，不构建供应商客户端。
 */
@ExtendWith(MockitoExtension.class)
class ModelGatewayImplTest {

    @Mock
    private PromptLoader promptLoader;
    @Mock
    private UserModelConfigurationReader configurationReader;
    @Mock
    private ModelClientConfiguration clients;
    @Mock
    private ChatMemory chatMemory;

    @Test
    void generate_whenUserNotConfigured_shouldThrowAndNotBuildClient() {
        when(configurationReader.find(any())).thenReturn(Optional.empty());
        ModelGatewayImpl gateway = gateway();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.generate(CurrentActor.of(1L), new ModelRequest("hi", null, null)));

        assertEquals(ErrorCode.USER_LLM_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(clients, never()).buildUserModel(any(), any());
    }

    @Test
    void generateStructured_whenUserNotConfigured_shouldThrowAndNotBuildClient() {
        when(configurationReader.find(any())).thenReturn(Optional.empty());
        ModelGatewayImpl gateway = gateway();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.generateStructured(CurrentActor.of(1L),
                        new ModelRequest("resume", "resume-import-parse", null), Sample.class));

        assertEquals(ErrorCode.USER_LLM_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(clients, never()).buildUserModel(any(), any());
    }

    @Test
    void operationParameters_whenMissing_shouldRejectBeforeBuildingClient() {
        ModelGatewayImpl gateway = gateway();
        ModelRequest input = new ModelRequest("hi", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> gateway.generateStructured(CurrentActor.of(1L), input, null));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.stream(CurrentActor.of(1L), null, input));

        verifyNoInteractions(configurationReader, clients, promptLoader, chatMemory);
    }

    private ModelGatewayImpl gateway() {
        return new ModelGatewayImpl(promptLoader, configurationReader, clients, chatMemory);
    }

    private static final class Sample {
    }
}
