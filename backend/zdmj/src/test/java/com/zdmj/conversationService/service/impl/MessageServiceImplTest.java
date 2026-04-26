package com.zdmj.conversationService.service.impl;

import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.config.RagConfig;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.conversationService.dto.MessageDTO;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.knowledgeService.service.KnowledgeRagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private ChatUtil chatUtil;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private KnowledgeRagService knowledgeRagService;
    @Mock
    private RagConfig ragConfig;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageService = spy(new MessageServiceImpl(
                redisUtil,
                chatUtil,
                messageMapper,
                conversationService,
                knowledgeRagService,
                ragConfig));
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void streamIdNull_whenResumeStream_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.resumeStream(null, 0));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("streamId不能为空", ex.getMessage());
        verify(redisUtil, never()).getString(any());
    }

    @Test
    void resumeStream_completed_shouldReplayAndDone() {
        Long streamId = 101L;
        doReturn("abcdef").when(redisUtil).getString("chat:stream:" + streamId + ":content");
        doReturn("completed").when(redisUtil).getString("chat:stream:" + streamId + ":status");

        List<ServerSentEvent<String>> events = messageService.resumeStream(streamId, 2).collectList().block();

        assertEquals(2, events.size());
        assertEquals("replay", events.get(0).event());
        String replayData = events.get(0).data();
        assertFalse(replayData == null);
        assertEquals(true, replayData.contains("\"cdef\""));
        assertEquals("done", events.get(1).event());
        assertEquals("[DONE]", events.get(1).data());
        verify(redisUtil).getString("chat:stream:" + streamId + ":content");
        verify(redisUtil).getString("chat:stream:" + streamId + ":status");
    }

    @Test
    void resumeStream_offsetNegative_shouldTreatAsZero() {
        Long streamId = 102L;
        doReturn("abc").when(redisUtil).getString("chat:stream:" + streamId + ":content");
        doReturn("completed").when(redisUtil).getString("chat:stream:" + streamId + ":status");

        List<ServerSentEvent<String>> events = messageService.resumeStream(streamId, -1).collectList().block();

        assertEquals("replay", events.get(0).event());
        String replayData = events.get(0).data();
        assertFalse(replayData == null);
        assertEquals(true, replayData.contains("\"abc\""));
        verify(redisUtil).getString("chat:stream:" + streamId + ":content");
    }

    @Test
    void resumeStream_failed_shouldReturnErrorEvent() {
        Long streamId = 103L;
        doReturn("hi").when(redisUtil).getString("chat:stream:" + streamId + ":content");
        doReturn("failed").when(redisUtil).getString("chat:stream:" + streamId + ":status");
        doReturn("persist failed").when(redisUtil).getString("chat:stream:" + streamId + ":error");

        List<ServerSentEvent<String>> events = messageService.resumeStream(streamId, 10).collectList().block();

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).event());
        String errorData = events.get(0).data();
        assertFalse(errorData == null);
        assertEquals(true, errorData.contains("persist failed"));
        verify(redisUtil).getString("chat:stream:" + streamId + ":error");
    }

    @Test
    void resumeStream_streamingWithLiveSink_shouldReplayAndDelta() {
        Long streamId = 104L;
        doReturn("abcd").when(redisUtil).getString("chat:stream:" + streamId + ":content");
        doReturn("streaming").when(redisUtil).getString("chat:stream:" + streamId + ":status");

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, Sinks.Many<String>> sinkMap =
                (ConcurrentHashMap<Long, Sinks.Many<String>>) ReflectionTestUtils.getField(
                        Objects.requireNonNull(messageService), "streamSinkMap");
        assertFalse(sinkMap == null);
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sinkMap.put(streamId, sink);

        Flux<ServerSentEvent<String>> flux = messageService.resumeStream(streamId, 2).take(2);
        sink.tryEmitNext("live");
        sink.tryEmitComplete();

        List<ServerSentEvent<String>> events = flux.collectList().block();

        assertEquals(2, events.size());
        assertEquals("replay", events.get(0).event());
        String replayData = events.get(0).data();
        assertFalse(replayData == null);
        assertEquals(true, replayData.contains("\"cd\""));
        assertEquals("delta", events.get(1).event());
        String deltaData = events.get(1).data();
        assertFalse(deltaData == null);
        assertEquals(true, deltaData.contains("\"live\""));
    }

    @Test
    void getMessages_limitCapped100_shouldReturnCappedPage() {
        Conversation conversation = new Conversation();
        conversation.setId(201L);
        doReturn(conversation).when(conversationService).getById(201L);
        doReturn(5).when(messageMapper).selectMessageCountByConversationId(201L);
        doReturn(List.of(new Message())).when(messageMapper).selectPageByConversationId(201L, 0, 100);

        PageDTO<Message> page = messageService.getMessagesByConversationId(201L, 1, 500);

        assertEquals(100, page.getLimit());
        assertEquals(1, page.getPage());
        assertEquals(5L, page.getTotal());
        assertEquals(1, page.getList().size());
        verify(messageMapper).selectPageByConversationId(201L, 0, 100);
    }

    @Test
    void getMessages_conversationAccessFailed_shouldPropagate() {
        doReturn(null).when(conversationService).getById(202L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> messageService.getMessagesByConversationId(202L, 1, 10));

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getMessage(), ex.getMessage());
        verify(messageMapper, never()).selectPageByConversationId(anyLong(), anyInt(), anyInt());
    }

    @Test
    void createStream_saveFailedOnUserMessage_shouldThrow9005() {
        MessageDTO dto = new MessageDTO();
        dto.setConversationId(301L);
        dto.setMessage("hello");
        Conversation conversation = new Conversation();
        conversation.setId(301L);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).getById(301L);
        doReturn(0).when(messageMapper).insert(any(Message.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(dto));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).insert(any(Message.class));
        verify(conversationService, never()).updateById(any(Conversation.class));
    }

    @Test
    void login_createStream_successCompleted_shouldPersistAndEmitMeta() {
        MessageDTO dto = new MessageDTO();
        dto.setConversationId(302L);
        dto.setMessage("hello");
        Conversation conversation = new Conversation();
        conversation.setId(302L);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).getById(302L);
        RagConfig.Rewrite rewrite = new RagConfig.Rewrite();
        rewrite.setEnabled(false);
        doReturn(rewrite).when(ragConfig).getRewrite();
        doReturn("title").when(chatUtil).chatOnce(anyString(), anyString(), any());
        doReturn(Flux.just("he", "llo")).when(chatUtil)
                .chatStreamInConversation(eq(302L), eq("hello"), anyString(), any());
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (insertTimes.incrementAndGet() == 2) {
                m.setId(900L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));
        doReturn(true).when(conversationService).updateById(any(Conversation.class));

        List<ServerSentEvent<String>> events = messageService.createStream(dto).collectList().block();

        assertFalse(events == null);
        assertEquals("meta", events.get(0).event());
        assertEquals(true, Objects.requireNonNull(events.get(0).data()).contains("\"streamId\":\"900\""));
        ArgumentCaptor<Message> assistantCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).updateById(assistantCaptor.capture());
        assertEquals("hello", assistantCaptor.getValue().getContent());
        verify(redisUtil).setString("chat:stream:900:status", "completed", 3600);
        verify(redisUtil).setString("chat:stream:900:done", "1", 3600);
    }

    @Test
    void login_createStream_secondInsertSaveFailed_shouldThrow9005() {
        MessageDTO dto = new MessageDTO();
        dto.setConversationId(303L);
        dto.setMessage("q");
        Conversation conversation = new Conversation();
        conversation.setId(303L);
        conversation.setMessageCount(2);
        doReturn(conversation).when(conversationService).getById(303L);
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> insertTimes.incrementAndGet() == 1 ? 1 : 0)
                .when(messageMapper).insert(any(Message.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(dto));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper, times(2)).insert(any(Message.class));
        verify(conversationService, never()).updateById(any(Conversation.class));
    }

    @Test
    void reset_createStream_streamError_shouldEmitFailedStatus() {
        MessageDTO dto = new MessageDTO();
        dto.setConversationId(304L);
        dto.setMessage("ask");
        Conversation conversation = new Conversation();
        conversation.setId(304L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).getById(304L);
        RagConfig.Rewrite rewrite = new RagConfig.Rewrite();
        rewrite.setEnabled(false);
        doReturn(rewrite).when(ragConfig).getRewrite();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(901L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(true).when(conversationService).updateById(any(Conversation.class));
        doReturn(Flux.concat(Flux.just("x"), Flux.error(new RuntimeException("boom"))))
                .when(chatUtil).chatStreamInConversation(eq(304L), eq("ask"), anyString(), any());

        assertThrows(RuntimeException.class, () -> messageService.createStream(dto).collectList().block());
        verify(redisUtil).setString("chat:stream:901:status", "failed", 3600);
        verify(redisUtil).setString("chat:stream:901:error", "boom", 3600);
    }

    @Test
    void update_createStream_updateFailedOnAssistantPersist_shouldEmitPersistError() {
        MessageDTO dto = new MessageDTO();
        dto.setConversationId(305L);
        dto.setMessage("go");
        Conversation conversation = new Conversation();
        conversation.setId(305L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).getById(305L);
        RagConfig.Rewrite rewrite = new RagConfig.Rewrite();
        rewrite.setEnabled(false);
        doReturn(rewrite).when(ragConfig).getRewrite();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(902L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(Flux.just("ok")).when(chatUtil).chatStreamInConversation(eq(305L), eq("go"), anyString(), any());
        doReturn(0).when(messageMapper).updateById(any(Message.class));
        doReturn(true).when(conversationService).updateById(any(Conversation.class));

        assertThrows(RuntimeException.class, () -> messageService.createStream(dto).collectList().block());
        verify(redisUtil).setString("chat:stream:902:status", "failed", 3600);
        verify(redisUtil).setString("chat:stream:902:error", "assistant message persist failed", 3600);
    }
}
