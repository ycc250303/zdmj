package com.zdmj.conversationService.service.impl;

import com.zdmj.conversationService.dto.ConversationDeletedEvent;
import java.time.LocalDateTime;
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
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.enums.MessageGenerationStatusEnum;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.knowledgeService.api.KnowledgeRagService;
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
    private KnowledgeRagService knowledgeRagService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        lenient().doCallRealMethod().when(transactionTemplate).executeWithoutResult(any());
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
                knowledgeRagService,
                transactionTemplate, java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
        lenient().doReturn(2).when(conversationMapper).incrementMessageCountAndGet(anyLong(), anyLong(), anyInt(), any(LocalDateTime.class));
        lenient().doReturn(1).when(conversationMapper).updateTitleByIdAndUserId(anyLong(), anyLong(), anyString(), any(LocalDateTime.class));
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

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(actor, dto).collectList().block(Duration.ofSeconds(5)));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).insert(any(Message.class));
    }

    @Test
    void createStream_withoutSubscription_shouldNotReadWriteOrStartAnswer() {
        messageService.createStream(actor, new ChatStreamRequest(500L, "问题"));
        org.mockito.Mockito.verifyNoInteractions(conversationService, conversationMapper, messageMapper,
                knowledgeRagService, transactionTemplate);
    }

    @Test
    void createStream_downstreamDemand_shouldControlModelConsumption() throws Exception {
        var request = prepareStream(501L);
        var produced = new AtomicInteger();
        var requested = new java.util.concurrent.atomic.AtomicLong();
        var started = new CountDownLatch(1);
        var received = new CountDownLatch(1);
        doReturn(Flux.range(1, 3).map(Object::toString).doOnNext(value -> produced.incrementAndGet())
                .doOnSubscribe(ignored -> started.countDown()).doOnRequest(requested::addAndGet))
                .when(knowledgeRagService).streamAnswer(eq(actor), eq(501L), any(), isNull(), eq(false));
        var subscriber = new reactor.core.publisher.BaseSubscriber<ServerSentEvent<String>>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) { }
            @Override
            protected void hookOnNext(ServerSentEvent<String> event) { received.countDown(); }
        };
        messageService.createStream(actor, request).subscribe(subscriber);
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(0, produced.get());
            assertEquals(0, requested.get());
            subscriber.request(1);
            assertTrue(received.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, produced.get());
            assertEquals(1, requested.get());
        } finally {
            subscriber.cancel();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(messageMapper).finishAssistant(anyLong(), eq(1L), eq(501L), eq("1"), eq(4)));
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
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())).when(knowledgeRagService)
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
        verify(conversationMapper).updateTitleByIdAndUserId(eq(302L), eq(1L), eq(title), any(LocalDateTime.class));
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

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(actor, dto).collectList().block(Duration.ofSeconds(5)));

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
                .when(knowledgeRagService).streamAnswer(eq(new CurrentActor(1L)), eq(304L), eq(new ModelRequest("ask", null, null)), isNull(), eq(false));

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
        doReturn(Flux.just("ok")).when(knowledgeRagService)
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
                () -> messageService.createStream(actor, dto).collectList().block(Duration.ofSeconds(5))).getErrorCode());
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void createStream_shouldUseConversationConfigForRag() {
        ChatStreamRequest dto = new ChatStreamRequest(306L, "ask");

        Conversation conversation = new Conversation();
        conversation.setId(306L);
        conversation.setConfig(Map.of(
                "useSystemKnowledge", true,
                "ragDocumentIds", List.of(9L)));
        doReturn(conversation).when(conversationService).requireOwned(actor, 306L);
        doReturn(conversation).when(conversationMapper).selectOwnedForUpdate(306L, 1L);
        doReturn(4).when(conversationMapper).incrementMessageCountAndGet(eq(306L), anyLong(), eq(2), any(LocalDateTime.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(m.getRole() == 2 ? 903L : 1L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        doReturn(Flux.just("ok")).when(knowledgeRagService).streamAnswer(eq(new CurrentActor(1L)), eq(306L), eq(new ModelRequest("ask", null, null)), eq(List.of(9L)), eq(true));

        messageService.createStream(actor, dto).collectList().block();

        verify(knowledgeRagService).streamAnswer(eq(new CurrentActor(1L)), eq(306L), eq(new ModelRequest("ask", null, null)), eq(List.of(9L)), eq(true));
    }

    @Test
    void createStream_whenClientCancels_shouldPersistPartialAndStopUpstream() throws Exception {
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
                .when(knowledgeRagService)
                .streamAnswer(eq(new CurrentActor(1L)), eq(307L), eq(new ModelRequest("wait", null, null)), isNull(), eq(false));

        var firstDelta = new CountDownLatch(1);
        Disposable subscription = messageService.createStream(actor, dto).subscribe(event -> firstDelta.countDown());
        assertTrue(firstDelta.await(5, TimeUnit.SECONDS));
        subscription.dispose();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(messageMapper).finishAssistant(eq(904L), eq(1L), eq(307L), eq("部分"), eq(4)));
        assertTrue(upstreamCancelled.get());
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    void createStream_synchronousRetrievalFailure_shouldSaveFailedStateAndPropagate() {
        var request = prepareStream(401L);
        doThrow(new IllegalStateException("检索服务不可用")).when(knowledgeRagService)
                .streamAnswer(eq(actor), eq(401L), any(ModelRequest.class), isNull(), eq(false));
        assertThrows(IllegalStateException.class, () -> messageService.createStream(actor, request).collectList().block());
        verify(messageMapper).finishAssistant(anyLong(), eq(1L), eq(401L), eq(""), eq(3));
    }

    @Test
    void createStream_modelAndPersistenceFail_shouldPropagateDatabaseCauseWithModelFailure() {
        var request = prepareStream(402L);
        doReturn(Flux.error(new IllegalArgumentException("模型异常"))).when(knowledgeRagService)
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
        assertThrows(IllegalStateException.class, () -> messageService.createStream(actor, request).collectList().block(Duration.ofSeconds(5)));
        verify(knowledgeRagService, never()).streamAnswer(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @ParameterizedTest
    @MethodSource("terminalSaveFailureCases")
    void createStream_terminalSaveFailure_shouldRetainProcessingAndRejectNextGeneration(
            MessageGenerationStatusEnum state, String failureStage) throws Exception {
        var request = prepareStream(404L);
        var answer = new AtomicReference<Message>();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(message.getRole() == 2 ? 905L : 1L);
            if (message.getRole() == 2) answer.set(message);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        IllegalStateException failure = new IllegalStateException("保存失败");
        String expectedMessage = switch (failureStage) {
            case "zeroRows" -> "生成中的助手回复必须更新一条记录";
            default -> "保存失败";
        };
        if (failureStage.equals("write")) {
            doThrow(failure).when(messageMapper)
                    .finishAssistant(905L, 1L, 404L, "部分内容", state.getCode());
        } else if (failureStage.equals("zeroRows")) {
            doReturn(0).when(messageMapper)
                    .finishAssistant(905L, 1L, 404L, "部分内容", state.getCode());
        }
        AtomicInteger transactions = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            if (transactions.incrementAndGet() == 1) {
                return callback.doInTransaction(new SimpleTransactionStatus());
            }
            Object result = callback.doInTransaction(new SimpleTransactionStatus());
            assertEquals(1, answer.get().getGenerationStatus());
            if (failureStage.equals("commit")) throw failure;
            return result;
        }).when(transactionTemplate).execute(any());
        Flux<String> tail = switch (state) {
            case COMPLETED -> Flux.empty();
            case FAILED -> Flux.error(new IllegalArgumentException("模型异常"));
            case CANCELLED -> Flux.never();
            default -> throw new IllegalArgumentException("不支持的终态");
        };
        doReturn(Flux.just("部分内容").concatWith(tail)).when(knowledgeRagService)
                .streamAnswer(eq(actor), eq(404L), any(), isNull(), eq(false));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MessageServiceImpl.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.list = new java.util.concurrent.CopyOnWriteArrayList<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var firstDelta = new CountDownLatch(1);
            var stream = messageService.createStream(actor, request).doOnNext(event -> firstDelta.countDown());
            if (state == MessageGenerationStatusEnum.CANCELLED) {
                var subscription = stream.subscribe();
                assertTrue(firstDelta.await(5, TimeUnit.SECONDS));
                subscription.dispose();
            } else {
                var error = assertThrows(IllegalStateException.class,
                        () -> stream.collectList().block(Duration.ofSeconds(5)));
                assertEquals(expectedMessage, error.getMessage());
            }
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage()
                            .contains("会话结束保存失败") && event.getThrowableProxy().getMessage().equals(expectedMessage))));
            assertEquals(1, answer.get().getGenerationStatus());
            assertEquals(ErrorCode.CONVERSATION_GENERATING, assertThrows(BusinessException.class,
                    () -> messageService.createStream(actor, request).collectList().block(Duration.ofSeconds(5)))
                    .getErrorCode());
            verify(messageMapper, times(2)).insert(any(Message.class));
            verify(messageMapper).finishAssistant(905L, 1L, 404L, "部分内容", state.getCode());
            assertEquals(2, transactions.get());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = MessageGenerationStatusEnum.class,
            names = {"COMPLETED", "FAILED", "CANCELLED"})
    void createStream_terminalSave_shouldUpdateMemoryOnlyAfterCommit(MessageGenerationStatusEnum state) throws Exception {
        var request = prepareStream(405L);
        var answer = new AtomicReference<Message>();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(message.getRole() == 2 ? 906L : 1L);
            if (message.getRole() == 2) answer.set(message);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            Object result = callback.doInTransaction(new SimpleTransactionStatus());
            assertEquals(1, answer.get().getGenerationStatus());
            return result;
        }).when(transactionTemplate).execute(any());
        Flux<String> tail = state == MessageGenerationStatusEnum.COMPLETED ? Flux.empty()
                : state == MessageGenerationStatusEnum.FAILED ? Flux.error(new IllegalStateException("模型异常")) : Flux.never();
        doReturn(Flux.just("部分内容").concatWith(tail)).when(knowledgeRagService)
                .streamAnswer(eq(actor), eq(405L), any(), isNull(), eq(false));
        var firstDelta = new CountDownLatch(1);
        var stream = messageService.createStream(actor, request).doOnNext(event -> firstDelta.countDown());
        if (state == MessageGenerationStatusEnum.CANCELLED) {
            var subscription = stream.subscribe();
            assertTrue(firstDelta.await(5, TimeUnit.SECONDS));
            subscription.dispose();
        } else if (state == MessageGenerationStatusEnum.FAILED) {
            assertThrows(IllegalStateException.class, () -> stream.collectList().block(Duration.ofSeconds(5)));
        } else {
            stream.collectList().block(Duration.ofSeconds(5));
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(state.getCode(), answer.get().getGenerationStatus()));
        verify(messageMapper).finishAssistant(906L, 1L, 405L, "部分内容", state.getCode());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = MessageGenerationStatusEnum.class,
            names = {"COMPLETED", "FAILED"})
    void createStream_cancelDuringTerminalSave_shouldFinishChosenStateWithoutInterruptingTransaction(
            MessageGenerationStatusEnum state) throws Exception {
        var request = prepareStream(406L);
        var saving = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var saved = new AtomicReference<Message>();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(message.getRole() == 2 ? 907L : 1L);
            if (message.getRole() == 2) saved.set(message);
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        var transactions = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            Object result = callback.doInTransaction(new SimpleTransactionStatus());
            if (transactions.incrementAndGet() == 2) {
                saving.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("等待结束事务释放超时");
            }
            return result;
        }).when(transactionTemplate).execute(any());
        Flux<String> tail = state == MessageGenerationStatusEnum.COMPLETED ? Flux.empty()
                : Flux.error(new IllegalArgumentException("模型异常"));
        doReturn(Flux.just("部分内容").concatWith(tail)).when(knowledgeRagService)
                .streamAnswer(eq(actor), eq(406L), any(), isNull(), eq(false));
        var subscription = messageService.createStream(actor, request).subscribe(ignored -> { }, ignored -> { });
        try {
            assertTrue(saving.await(5, TimeUnit.SECONDS));
            subscription.dispose();
            release.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(state.getCode(), saved.get().getGenerationStatus()));
            verify(messageMapper).finishAssistant(907L, 1L, 406L, "部分内容", state.getCode());
            assertEquals(2, transactions.get());
        } finally {
            release.countDown();
            subscription.dispose();
        }
    }

    @Test
    void deletionNotice_shouldOnlyCancelMatchingConversationAndRejectUnauthorizedGeneration() throws Exception {
        var request = prepareStream(502L);
        var otherActor = new CurrentActor(2L);
        doThrow(new BusinessException(ErrorCode.NO_PERMISSION))
                .when(conversationService).requireOwned(otherActor, 502L);
        var received = new CountDownLatch(1);
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        doReturn(Flux.just("内容").concatWith(Flux.never()).doOnCancel(() -> cancelled.set(true)))
                .when(knowledgeRagService).streamAnswer(eq(actor), eq(502L), any(), isNull(), eq(false));
        var completed = messageService.createStream(actor, request).doOnNext(event -> received.countDown())
                .collectList().toFuture();
        try {
            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertEquals(ErrorCode.NO_PERMISSION, assertThrows(BusinessException.class,
                    () -> messageService.createStream(otherActor, request).collectList()
                            .block(Duration.ofSeconds(5))).getErrorCode());
            verify(knowledgeRagService, never()).streamAnswer(eq(otherActor), anyLong(), any(), any(), eq(false));
            messageService.cancelDeletedConversation(new ConversationDeletedEvent(actor, 999L));
            assertFalse(cancelled.get());
            assertFalse(completed.isDone());
            assertEquals(ErrorCode.CONVERSATION_GENERATING, assertThrows(BusinessException.class,
                    () -> messageService.createStream(actor, request).collectList().block(Duration.ofSeconds(5))).getErrorCode());
            messageService.cancelDeletedConversation(new ConversationDeletedEvent(actor, 502L));
            assertEquals(1, completed.get(5, TimeUnit.SECONDS).size());
            assertTrue(cancelled.get());
            verify(messageMapper, never()).finishAssistant(anyLong(), anyLong(), anyLong(), anyString(), anyInt());
        } finally {
            completed.cancel(true);
        }
    }

    @Test
    void earlierCompletion_shouldNotReleaseLaterGeneration() throws Exception {
        var request = prepareStream(503L);
        var saving = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var received = new CountDownLatch(1);
        var transactions = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            Object result = callback.doInTransaction(new SimpleTransactionStatus());
            if (transactions.incrementAndGet() == 2) {
                saving.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("等待结束事务释放超时");
            }
            return result;
        }).when(transactionTemplate).execute(any());
        doReturn(Flux.just("第一轮"), Flux.just("第二轮").concatWith(Flux.never()))
                .when(knowledgeRagService).streamAnswer(eq(actor), eq(503L), any(), isNull(), eq(false));
        var first = messageService.createStream(actor, request).collectList().toFuture();
        Disposable second = null;
        try {
            assertTrue(saving.await(5, TimeUnit.SECONDS));
            messageService.cancelDeletedConversation(new ConversationDeletedEvent(actor, 503L));
            second = messageService.createStream(actor, request).subscribe(event -> received.countDown());
            assertTrue(received.await(5, TimeUnit.SECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertEquals(ErrorCode.CONVERSATION_GENERATING, assertThrows(BusinessException.class,
                    () -> messageService.createStream(actor, request).collectList().block(Duration.ofSeconds(5))).getErrorCode());
        } finally {
            release.countDown();
            first.cancel(true);
            if (second != null) second.dispose();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(messageMapper).finishAssistant(4L, 1L, 503L, "第二轮", 4));
    }

    private static Stream<Arguments> terminalSaveFailureCases() {
        return Stream.of(MessageGenerationStatusEnum.COMPLETED, MessageGenerationStatusEnum.FAILED,
                        MessageGenerationStatusEnum.CANCELLED)
                .flatMap(state -> Stream.of("write", "zeroRows", "commit")
                        .map(stage -> Arguments.of(state, stage)));
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
