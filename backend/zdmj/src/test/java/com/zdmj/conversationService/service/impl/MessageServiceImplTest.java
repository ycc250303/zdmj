package com.zdmj.conversationService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.aiService.api.ModelRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.support.ConversationContextSupport;
import com.zdmj.knowledgeService.service.KnowledgeAnswerService;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    private final CurrentActor actor = new CurrentActor(1L);

    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ConversationServiceImpl conversationService;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private KnowledgeAnswerService knowledgeAnswerService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        lenient().when(conversationMapper.selectOwnedForUpdate(anyLong(), anyLong())).thenAnswer(inv -> {
            Conversation c = new Conversation();
            c.setId(inv.getArgument(0));
            c.setUserId(inv.getArgument(1));
            c.setMessageCount(0);
            return c;
        });
        messageService = new MessageServiceImpl(
                messageMapper,
                conversationService,
                conversationMapper,
                knowledgeAnswerService,
                transactionTemplate);
        lenient().doReturn(2).when(conversationMapper).incrementMessageCountAndGet(anyLong(), anyLong(), anyInt());
        lenient().doReturn(1).when(conversationMapper).updateTitleByIdAndUserId(anyLong(), anyLong(), anyString());
    }

    @Test
    void getMessages_limitCapped100_shouldReturnCappedPage() {
        Conversation conversation = new Conversation();
        conversation.setId(201L);
        doReturn(conversation).when(conversationService).requireOwned(actor, 201L);
        Page<Message> mpPage = new Page<>(1, 100);
        mpPage.setRecords(List.of(new Message()));
        mpPage.setTotal(5);
        doReturn(mpPage).when(messageMapper).selectPageByConversationId(any(Page.class), eq(201L));

        PageDTO<MessageResponse> page = messageService.getMessagesByConversationId(actor, 201L, 1, 500);

        assertEquals(100, page.getLimit());
        assertEquals(1, page.getPage());
        assertEquals(5L, page.getTotal());
        assertEquals(1, page.getList().size());
        verify(messageMapper).selectPageByConversationId(any(Page.class), eq(201L));
    }

    @Test
    void getMessages_conversationAccessFailed_shouldPropagate() {
        doThrow(new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND))
                .when(conversationService).requireOwned(actor, 202L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> messageService.getMessagesByConversationId(actor, 202L, 1, 10));

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getMessage(), ex.getMessage());
        verify(messageMapper, never()).selectPageByConversationId(any(), anyLong());
    }

    @Test
    void createStream_saveFailedOnUserMessage_shouldThrow9005() {
        ChatStreamRequest dto = new ChatStreamRequest(301L, "hello");

        Conversation conversation = new Conversation();
        conversation.setId(301L);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).requireOwned(actor, 301L);
        doReturn(0).when(messageMapper).insert(any(Message.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(actor, dto));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).insert(any(Message.class));
    }

    @ParameterizedTest
    @MethodSource("titleCases")
    void login_createStream_successCompleted_shouldPersistTitleAndEmitDelta(String message, String title) {
        ChatStreamRequest dto = new ChatStreamRequest(302L, message);

        Conversation conversation = new Conversation();
        conversation.setId(302L);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).requireOwned(actor, 302L);
        Thread caller = Thread.currentThread();
        doReturn(Flux.defer(() -> {
            assertFalse(Thread.currentThread() == caller);
            return Flux.just("he", "llo");
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())).when(knowledgeAnswerService)
                .streamAnswer(same(actor), eq(302L), eq(new ModelRequest(message, null, null)), isNull(), eq(false));
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId((long) insertTimes.incrementAndGet());
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());

        List<ServerSentEvent<String>> events = messageService.createStream(actor, dto).collectList().block();

        assertEquals(2, events.size());
        assertEquals("delta", events.get(0).event());
        assertTrue(events.get(0).data().contains("\"he\""));
        assertEquals("delta", events.get(1).event());
        assertTrue(events.get(1).data().contains("\"llo\""));
        verify(messageMapper).finishAssistant(anyLong(), eq(actor.userId()), eq(302L), eq("hello"), eq(2));
        verify(conversationMapper).updateTitleByIdAndUserId(302L, 1L, title);
    }

    private static Stream<Arguments> titleCases() {
        return Stream.of(
                Arguments.of("hello", "hello"),
                Arguments.of("  hello\n\tworld  ", "hello world"),
                Arguments.of("😀".repeat(30), "😀".repeat(30)),
                Arguments.of("😀".repeat(31), "😀".repeat(30) + "…"));
    }

    @Test
    void login_createStream_secondInsertSaveFailed_shouldThrow9005() {
        ChatStreamRequest dto = new ChatStreamRequest(303L, "q");

        Conversation conversation = new Conversation();
        conversation.setId(303L);
        conversation.setMessageCount(2);
        doReturn(conversation).when(conversationService).requireOwned(actor, 303L);
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(1L);
            return insertTimes.incrementAndGet() == 1 ? 1 : 0;
        })
                .when(messageMapper).insert(any(Message.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(actor, dto));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper, times(2)).insert(any(Message.class));
    }

    @Test
    void reset_createStream_streamError_shouldPersistPartialAndEmitError() {
        ChatStreamRequest dto = new ChatStreamRequest(304L, "ask");

        Conversation conversation = new Conversation();
        conversation.setId(304L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(actor, 304L);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(m.getRole() == 2 ? 901L : 1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        doReturn(Flux.concat(Flux.just("x"), Flux.error(new RuntimeException("boom"))))
                .when(knowledgeAnswerService).streamAnswer(eq(new CurrentActor(1L)), eq(304L), eq(new ModelRequest("ask", null, null)), isNull(), eq(false));

        assertThrows(RuntimeException.class, () -> messageService.createStream(actor, dto).collectList().block());
        verify(messageMapper).finishAssistant(eq(901L), eq(1L), eq(304L), eq("x"), eq(3));
    }

    @Test
    void update_createStream_updateFailedOnAssistantPersist_shouldEmitPersistError() {
        ChatStreamRequest dto = new ChatStreamRequest(305L, "go");

        Conversation conversation = new Conversation();
        conversation.setId(305L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(actor, 305L);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(m.getRole() == 2 ? 902L : 1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(Flux.just("ok")).when(knowledgeAnswerService)
                .streamAnswer(eq(new CurrentActor(1L)), eq(305L), eq(new ModelRequest("go", null, null)), isNull(), eq(false));
        doReturn(0).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());

        assertThrows(RuntimeException.class, () -> messageService.createStream(actor, dto).collectList().block());
        verify(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    void createStream_sameConversationProcessing_shouldRejectBeforeInserting() {
        Conversation conversation = new Conversation();
        conversation.setId(400L);
        doReturn(conversation).when(conversationService).requireOwned(actor, 400L);
        doReturn(true).when(messageMapper).hasProcessing(1L, 400L);
        ChatStreamRequest dto = new ChatStreamRequest(400L, "hello");

        assertEquals(ErrorCode.CONVERSATION_GENERATING, assertThrows(BusinessException.class,
                () -> messageService.createStream(actor, dto)).getErrorCode());
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void createStream_shouldUseConversationConfigForRag() {
        ChatStreamRequest dto = new ChatStreamRequest(306L, "ask");

        Conversation conversation = new Conversation();
        conversation.setId(306L);
        conversation.setConfig(Map.of(
                ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, true,
                ConversationContextSupport.CONFIG_RAG_DOCUMENT_IDS, List.of(9L)));
        doReturn(conversation).when(conversationService).requireOwned(actor, 306L);
        doReturn(conversation).when(conversationMapper).selectOwnedForUpdate(306L, 1L);
        doReturn(4).when(conversationMapper).incrementMessageCountAndGet(eq(306L), anyLong(), eq(2));
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(m.getRole() == 2 ? 903L : 1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        doReturn(Flux.just("ok")).when(knowledgeAnswerService).streamAnswer(eq(new CurrentActor(1L)), eq(306L), eq(new ModelRequest("ask", null, null)), eq(List.of(9L)), eq(true));

        messageService.createStream(actor, dto).collectList().block();

        verify(knowledgeAnswerService).streamAnswer(eq(new CurrentActor(1L)), eq(306L), eq(new ModelRequest("ask", null, null)), eq(List.of(9L)), eq(true));
    }

    @Test
    void createStream_whenClientCancels_shouldPersistPartialAndStopUpstream() {
        ChatStreamRequest dto = new ChatStreamRequest(307L, "wait");

        Conversation conversation = new Conversation();
        conversation.setId(307L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(actor, 307L);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(message.getRole() == 2 ? 904L : 1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        java.util.concurrent.atomic.AtomicBoolean upstreamCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        doReturn(Flux.concat(Flux.just("部分"), Flux.never()).doOnCancel(() -> upstreamCancelled.set(true)))
                .when(knowledgeAnswerService)
                .streamAnswer(eq(new CurrentActor(1L)), eq(307L), eq(new ModelRequest("wait", null, null)), isNull(), eq(false));

        Disposable subscription = messageService.createStream(actor, dto).subscribe();
        subscription.dispose();

        verify(messageMapper).finishAssistant(eq(904L), eq(1L), eq(307L), eq("部分"), eq(4));
        assertTrue(upstreamCancelled.get());
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    void createStream_synchronousRetrievalFailure_shouldSaveFailedStateAndPropagate() {
        var request = prepareStream(401L);
        doThrow(new IllegalStateException("检索服务不可用")).when(knowledgeAnswerService)
                .streamAnswer(eq(actor), eq(401L), any(ModelRequest.class), isNull(), eq(false));
        assertThrows(IllegalStateException.class, () -> messageService.createStream(actor, request).collectList().block());
        verify(messageMapper).finishAssistant(anyLong(), eq(1L), eq(401L), eq(""), eq(3));
    }

    @Test
    void createStream_modelAndPersistenceFail_shouldPropagateDatabaseCauseWithModelFailure() {
        var request = prepareStream(402L);
        doReturn(Flux.error(new IllegalArgumentException("模型异常"))).when(knowledgeAnswerService)
                .streamAnswer(eq(actor), eq(402L), any(ModelRequest.class), isNull(), eq(false));
        doThrow(new IllegalStateException("数据库异常")).when(messageMapper)
                .finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        var error = assertThrows(IllegalStateException.class,
                () -> messageService.createStream(actor, request).collectList().block());
        assertEquals("数据库异常", error.getMessage());
        assertTrue(java.util.Arrays.stream(error.getSuppressed()).anyMatch(e -> "模型异常".equals(e.getMessage())));
    }

    @Test
    void createStream_insertDoesNotFillId_shouldStopBeforeAnswer() {
        var request = prepareStream(403L);
        doReturn(1).when(messageMapper).insert(any(Message.class));
        assertThrows(IllegalStateException.class, () -> messageService.createStream(actor, request));
        verify(knowledgeAnswerService, never()).streamAnswer(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private ChatStreamRequest prepareStream(long id) {
        var conversation = new Conversation();
        conversation.setId(id);
        doReturn(conversation).when(conversationService).requireOwned(actor, id);
        AtomicInteger ids = new AtomicInteger();
        lenient().doAnswer(inv -> {
            Message message = inv.getArgument(0);
            message.setId((long) ids.incrementAndGet());
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        lenient().doReturn(1).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        var request = new ChatStreamRequest(id, "任务");

        return request;
    }
}
