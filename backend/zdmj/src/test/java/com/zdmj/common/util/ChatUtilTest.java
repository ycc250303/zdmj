package com.zdmj.common.util;

import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatUtilTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private ChatUtil chatUtil;

    @BeforeEach
    void setUp() {
        chatUtil = new ChatUtil(chatClient);
    }

    @Test
    void testChat() {
        String message = "hello";
        String expected = "world";

        when(chatClient.prompt()
                .user(message)
                .call()
                .content()).thenReturn(expected);

        String actual = chatUtil.chat(message);

        assertEquals(expected, actual);
    }

    @Test
    void testChatStream() {
        String message = "hello";
        Flux<String> expected = Flux.just("a", "b", "c");

        when(chatClient.prompt()
                .user(message)
                .stream()
                .content()).thenReturn(expected);

        Flux<String> actual = chatUtil.chatStream(message);

        assertEquals(java.util.List.of("a", "b", "c"), actual.collectList().block());
    }
}
