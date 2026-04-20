package com.zdmj.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatUtilTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient statelessChatClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClientWithMemory;

    @Mock
    private PromptUtil promptUtil;

    private ChatUtil chatUtil;

    @BeforeEach
    void setUp() {
        chatUtil = new ChatUtil(statelessChatClient, chatClientWithMemory, promptUtil);
    }

    @Test
    void testChatOnce() {
        String message = "hello";
        String expected = "world";

        when(statelessChatClient.prompt()
                .user(message)
                .call()
                .content()).thenReturn(expected);

        String actual = chatUtil.chatOnce(message, null, null);

        assertEquals(expected, actual);
    }

    @Test
    void testChatStreamOnce() {
        String message = "hello";
        Flux<String> expected = Flux.just("a", "b", "c");

        when(statelessChatClient.prompt()
                .user(message)
                .stream()
                .content()).thenReturn(expected);

        Flux<String> actual = chatUtil.chatStreamOnce(message, null, null);

        assertEquals(java.util.List.of("a", "b", "c"), actual.collectList().block());
    }
}
