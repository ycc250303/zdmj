package com.zdmj.common.ai;

import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证结构化路径走 JSON Mode + {@code entity(converter)}，且对话路径不带 JSON Mode。
 */
@ExtendWith(MockitoExtension.class)
class ChatUtilStructuredParseTest {

    @Mock
    private PromptUtil promptUtil;
    @Mock
    private UserLlmRouter userLlmRouter;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClientRequestSpec spec;
    @Mock
    private CallResponseSpec callSpec;

    private ChatUtil chatUtil;

    @BeforeEach
    void setUp() {
        chatUtil = new ChatUtil(promptUtil, userLlmRouter);
        lenient().when(userLlmRouter.getChatClient(1L)).thenReturn(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(spec);
        lenient().when(spec.options(any(ChatOptions.class))).thenReturn(spec);
        lenient().when(spec.user(anyString())).thenReturn(spec);
        lenient().when(spec.call()).thenReturn(callSpec);
    }

    @Test
    void chatStructuredOnce_whenMultilineJsonFence_shouldParse() {
        stubEntityConvert("""
                ```json
                {"name":"Ada","score":9}
                ```
                """);

        SampleOut parsed = chatUtil.chatStructuredOnce(1L, "msg", null, null, SampleOut.class);

        assertEquals("Ada", parsed.getName());
        assertEquals(9, parsed.getScore());
    }

    @Test
    void chatStructuredOnce_whenSingleLineJsonFence_shouldParse() {
        stubEntityConvert("```json {\"name\":\"Dee\",\"score\":4} ```");

        SampleOut parsed = chatUtil.chatStructuredOnce(1L, "msg", null, null, SampleOut.class);

        assertEquals("Dee", parsed.getName());
        assertEquals(4, parsed.getScore());
    }

    @Test
    void chatStructuredOnce_whenRawJson_shouldParse() {
        stubEntityConvert("{\"name\":\"Cara\",\"score\":1}");

        SampleOut parsed = chatUtil.chatStructuredOnce(1L, "msg", null, null, SampleOut.class);

        assertEquals("Cara", parsed.getName());
        assertEquals(1, parsed.getScore());
    }

    @Test
    void chatStructuredOnce_shouldEnableJsonObjectModeAndKeepJsonWordInUserMessage() {
        stubEntityConvert("{\"name\":\"Cara\",\"score\":1}");

        chatUtil.chatStructuredOnce(1L, "简历原文", null, null, SampleOut.class);

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
    void chatStructuredOnce_whenEntityNull_shouldThrow() {
        when(callSpec.entity(any(StructuredOutputConverter.class))).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> chatUtil.chatStructuredOnce(1L, "msg", null, null, SampleOut.class));
    }

    @Test
    void chatOnce_shouldNotSetJsonObjectOptions() {
        when(callSpec.content()).thenReturn("ok");

        String text = chatUtil.chatOnce(1L, "hi", null, null);

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

    @Data
    static class SampleOut {
        private String name;
        private int score;
    }
}
