package com.zdmj.aiService.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.support.ModelClientFactory;
import com.zdmj.aiService.support.UserModelConfigurationReader.ResolvedConfiguration;
import com.zdmj.aiService.support.UserModelConfigurationReader;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.prompt.PromptLoader;

import reactor.core.publisher.Flux;

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
    private ModelClientFactory clients;
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

    @Test
    void calls_shouldReadCurrentConfigurationAndOnlyRememberConversationMessages() {
        CurrentActor actor = CurrentActor.of(1L);
        var first = new ResolvedConfiguration(
                ModelCode.DEEPSEEK_FLASH, "first-key");
        var updated = new ResolvedConfiguration(
                ModelCode.QWEN_PLUS, "updated-key");
        when(configurationReader.find(actor)).thenReturn(Optional.of(first), Optional.of(updated),
                Optional.of(updated), Optional.empty());
        var model = org.mockito.Mockito.mock(ChatModel.class);
        var reply = new ChatResponse(List.of(
                new Generation(
                        new AssistantMessage("reply"))));
        when(model.call(any(Prompt.class))).thenReturn(reply);
        when(model.stream(any(Prompt.class)))
                .thenReturn(Flux.just(reply));
        when(clients.buildUserModel(any(), any())).thenReturn(model);
        var memory = MessageWindowChatMemory.builder().build();
        var gateway = new ModelGatewayImpl(promptLoader, configurationReader, clients, memory);

        assertEquals("reply", gateway.stream(actor, 11L, new ModelRequest("first", null, null))
                .collectList().block(Duration.ofSeconds(5)).getFirst());
        assertEquals("reply", gateway.generate(actor, new ModelRequest("plain", null, null)));
        assertEquals("reply", gateway.stream(actor, 12L, new ModelRequest("second", null, null))
                .collectList().block(Duration.ofSeconds(5)).getFirst());
        assertEquals(List.of("first", "reply"), memory.get("11").stream()
                .map(Message::getText).toList());
        assertEquals(List.of("second", "reply"), memory.get("12").stream()
                .map(Message::getText).toList());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertEquals(List.of("plain"), prompt.getValue().getInstructions().stream()
                .map(Message::getText).toList());
        verify(clients).buildUserModel(first.model(), first.apiKey());
        verify(clients, org.mockito.Mockito.times(2)).buildUserModel(updated.model(), updated.apiKey());
        assertEquals(ErrorCode.USER_LLM_NOT_CONFIGURED, assertThrows(BusinessException.class,
                () -> gateway.generate(actor, new ModelRequest("deleted", null, null))).getErrorCode());
    }

    private ModelGatewayImpl gateway() {
        return new ModelGatewayImpl(promptLoader, configurationReader, clients, chatMemory);
    }

    private static final class Sample {
    }
}
