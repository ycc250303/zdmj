package com.zdmj.aiService.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.api.StructuredModelRequest;
import com.zdmj.aiService.config.ModelClientConfiguration;
import com.zdmj.aiService.provider.UserModelConfigurationProvider;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.context.CurrentActor;

import lombok.Data;

/**
 * 验证结构化路径走 JSON Mode + {@code entity(converter)}，且文本路径不带 JSON Mode。
 */
@ExtendWith(MockitoExtension.class)
class ModelGatewayStructuredParseTest {

    @Mock
    private PromptUtil promptUtil;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClientRequestSpec spec;
    @Mock
    private CallResponseSpec callSpec;

    private ModelGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new FixedClientGateway(promptUtil, chatClient);
        lenient().when(chatClient.prompt()).thenReturn(spec);
        lenient().when(spec.options(any(ChatOptions.class))).thenReturn(spec);
        lenient().when(spec.user(anyString())).thenReturn(spec);
        lenient().when(spec.call()).thenReturn(callSpec);
    }

    @Test
    void generateStructured_whenSingleLineJsonFence_shouldParse() {
        stubEntityConvert("```json {\"name\":\"Dee\",\"score\":4} ```");

        SampleOut parsed = gateway.generateStructured(
                CurrentActor.of(1L), StructuredModelRequest.of("msg", null, null, SampleOut.class));

        assertEquals("Dee", parsed.getName());
        assertEquals(4, parsed.getScore());
    }

    @Test
    void generateStructured_whenRawJson_shouldParse() {
        stubEntityConvert("{\"name\":\"Cara\",\"score\":1}");

        SampleOut parsed = gateway.generateStructured(
                CurrentActor.of(1L), StructuredModelRequest.of("msg", null, null, SampleOut.class));

        assertEquals("Cara", parsed.getName());
        assertEquals(1, parsed.getScore());
    }

    @Test
    void generateStructured_shouldEnableJsonObjectModeAndKeepJsonWordInUserMessage() {
        stubEntityConvert("{\"name\":\"Cara\",\"score\":1}");

        gateway.generateStructured(
                CurrentActor.of(1L), StructuredModelRequest.of("简历原文", null, null, SampleOut.class));

        ArgumentCaptor<ChatOptions> optionsCaptor = ArgumentCaptor.forClass(ChatOptions.class);
        verify(spec).options(optionsCaptor.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) optionsCaptor.getValue();
        assertEquals(ResponseFormat.Type.JSON_OBJECT, options.getResponseFormat().getType());

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).user(userCaptor.capture());
        assertTrue(userCaptor.getValue().contains("简历原文"));
        assertTrue(userCaptor.getValue().toLowerCase().contains("json"));
    }

    @Test
    void generateStructured_whenEntityNull_shouldThrow() {
        when(callSpec.entity(any(StructuredOutputConverter.class))).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> gateway.generateStructured(
                        CurrentActor.of(1L), StructuredModelRequest.of("msg", null, null, SampleOut.class)));
    }

    @Test
    void generate_shouldNotSetJsonObjectOptions() {
        when(callSpec.content()).thenReturn("ok");

        String text = gateway.generate(CurrentActor.of(1L), new ModelRequest("hi", null, null));

        assertEquals("ok", text);
        verify(spec, never()).options(any());
    }

    @SuppressWarnings("unchecked")
    private void stubEntityConvert(String llmText) {
        when(callSpec.entity(any(StructuredOutputConverter.class))).thenAnswer(invocation -> {
            StructuredOutputConverter<SampleOut> converter = invocation.getArgument(0);
            return converter.convert(llmText);
        });
    }

    private static final class FixedClientGateway extends ModelGatewayImpl {

        private final ChatClient fixedClient;

        private FixedClientGateway(PromptUtil promptUtil, ChatClient fixedClient) {
            super(promptUtil, mock(UserModelConfigurationProvider.class), mock(ModelClientConfiguration.class),
                    mock(ChatMemory.class));
            this.fixedClient = fixedClient;
        }

        @Override
        protected ChatClient userChatClient(CurrentActor actor, boolean withMemory) {
            return fixedClient;
        }
    }

    @Data
    static class SampleOut {
        private String name;
        private int score;
    }
}
