package com.zdmj.conversationService.service.impl;

import com.zdmj.common.context.CurrentActor;
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
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.conversationService.support.ConversationContextSupport;
import com.zdmj.knowledgeService.service.KnowledgeRagService;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    private final CurrentActor actor = new CurrentActor(1L);

    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private KnowledgeRagService knowledgeRagService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        messageService = new MessageServiceImpl(
                messageMapper,
                conversationService,
                conversationMapper,
                knowledgeRagService,
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
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(301L);
        dto.setMessage("hello");
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
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(302L);
        dto.setMessage(message);
        Conversation conversation = new Conversation();
        conversation.setId(302L);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).requireOwned(actor, 302L);
        Thread caller = Thread.currentThread();
        doReturn(Flux.defer(() -> {
            assertFalse(Thread.currentThread() == caller);
            return Flux.just("he", "llo");
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())).when(knowledgeRagService)
                .streamAnswer(same(actor), eq(302L), eq(message), isNull(), eq(false), any());
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (insertTimes.incrementAndGet() == 2) {
                m.setId(900L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));

        List<ServerSentEvent<String>> events = messageService.createStream(actor, dto).collectList().block();

        assertEquals(2, events.size());
        assertEquals("delta", events.get(0).event());
        assertTrue(events.get(0).data().contains("\"he\""));
        assertEquals("delta", events.get(1).event());
        assertTrue(events.get(1).data().contains("\"llo\""));
        ArgumentCaptor<Message> assistantCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).updateById(assistantCaptor.capture());
        assertEquals("hello", assistantCaptor.getValue().getContent());
        assertEquals(actor.userId(), assistantCaptor.getValue().getUserId());
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
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(303L);
        dto.setMessage("q");
        Conversation conversation = new Conversation();
        conversation.setId(303L);
        conversation.setMessageCount(2);
        doReturn(conversation).when(conversationService).requireOwned(actor, 303L);
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> insertTimes.incrementAndGet() == 1 ? 1 : 0)
                .when(messageMapper).insert(any(Message.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(actor, dto));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper, times(2)).insert(any(Message.class));
    }

    @Test
    void reset_createStream_streamError_shouldPersistPartialAndEmitError() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(304L);
        dto.setMessage("ask");
        Conversation conversation = new Conversation();
        conversation.setId(304L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(actor, 304L);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(901L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));
        doReturn(Flux.concat(Flux.just("x"), Flux.error(new RuntimeException("boom"))))
                .when(knowledgeRagService).streamAnswer(eq(new CurrentActor(1L)), eq(304L), eq("ask"), isNull(), eq(false), any());

        assertThrows(RuntimeException.class, () -> messageService.createStream(actor, dto).collectList().block());
        ArgumentCaptor<Message> assistantCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).updateById(assistantCaptor.capture());
        assertEquals("x", assistantCaptor.getValue().getContent());
    }

    @Test
    void update_createStream_updateFailedOnAssistantPersist_shouldEmitPersistError() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(305L);
        dto.setMessage("go");
        Conversation conversation = new Conversation();
        conversation.setId(305L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(actor, 305L);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(902L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(Flux.just("ok")).when(knowledgeRagService)
                .streamAnswer(eq(new CurrentActor(1L)), eq(305L), eq("go"), isNull(), eq(false), any());
        doReturn(0).when(messageMapper).updateById(any(Message.class));

        assertThrows(RuntimeException.class, () -> messageService.createStream(actor, dto).collectList().block());
        verify(messageMapper).updateById(any(Message.class));
    }

    @Test
    void createStream_concurrentSameConversation_shouldAllocateUniqueSequentialSequences() throws Exception {
        Long conversationId = 400L;
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).requireOwned(actor, conversationId);

        doReturn(Flux.just("ok")).when(knowledgeRagService)
                .streamAnswer(eq(new CurrentActor(1L)), eq(conversationId), anyString(), isNull(), eq(false), any());

        AtomicInteger counter = new AtomicInteger(0);
        doReturn(1).when(conversationMapper).updateTitleByIdAndUserId(eq(conversationId), anyLong(), anyString());
        org.mockito.Mockito.doAnswer(inv -> counter.addAndGet(2))
                .when(conversationMapper).incrementMessageCountAndGet(eq(conversationId), anyLong(), eq(2));

        ConcurrentLinkedQueue<Integer> insertedSequences = new ConcurrentLinkedQueue<>();
        AtomicInteger assistantIdSeed = new AtomicInteger(10000);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            insertedSequences.add(m.getSequence());
            if (m.getRole() != null && m.getRole() == 2) {
                m.setId((long) assistantIdSeed.incrementAndGet());
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));

        ChatStreamRequest dto1 = new ChatStreamRequest();
        dto1.setConversationId(conversationId);
        dto1.setMessage("hello-1");
        ChatStreamRequest dto2 = new ChatStreamRequest();
        dto2.setConversationId(conversationId);
        dto2.setMessage("hello-2");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<ServerSentEvent<String>>> f1 = pool.submit(() -> messageService.createStream(actor, dto1).collectList().block());
            Future<List<ServerSentEvent<String>>> f2 = pool.submit(() -> messageService.createStream(actor, dto2).collectList().block());

            List<ServerSentEvent<String>> e1 = f1.get(5, TimeUnit.SECONDS);
            List<ServerSentEvent<String>> e2 = f2.get(5, TimeUnit.SECONDS);
            assertFalse(e1 == null || e1.isEmpty());
            assertFalse(e2 == null || e2.isEmpty());

            assertEquals(4, insertedSequences.size());
            Set<Integer> seqSet = Set.copyOf(insertedSequences);
            assertEquals(4, seqSet.size());
            assertTrue(seqSet.containsAll(Set.of(1, 2, 3, 4)));

            verify(conversationMapper, times(2))
                    .incrementMessageCountAndGet(eq(conversationId), anyLong(), eq(2));
            verify(conversationMapper, times(1))
                    .updateTitleByIdAndUserId(eq(conversationId), anyLong(),
                            org.mockito.ArgumentMatchers.argThat(title -> Set.of("hello-1", "hello-2").contains(title)));
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void createStream_shouldUseConversationConfigForRag() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(306L);
        dto.setMessage("ask");
        Conversation conversation = new Conversation();
        conversation.setId(306L);
        conversation.setConfig(Map.of(
                ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, true,
                ConversationContextSupport.CONFIG_RAG_DOCUMENT_IDS, List.of(9L)));
        doReturn(conversation).when(conversationService).requireOwned(actor, 306L);
        doReturn(4).when(conversationMapper).incrementMessageCountAndGet(eq(306L), anyLong(), eq(2));
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(903L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));
        doReturn(Flux.just("ok")).when(knowledgeRagService).streamAnswer(eq(new CurrentActor(1L)), eq(306L), eq("ask"), eq(List.of(9L)), eq(true), any());

        messageService.createStream(actor, dto).collectList().block();

        verify(knowledgeRagService).streamAnswer(eq(new CurrentActor(1L)), eq(306L), eq("ask"), eq(List.of(9L)), eq(true), any());
    }

    @Test
    void createStream_whenClientCancels_shouldPersistPartialAndStopUpstream() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(307L);
        dto.setMessage("wait");
        Conversation conversation = new Conversation();
        conversation.setId(307L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(actor, 307L);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message.getRole() == 2) {
                message.setId(904L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));
        java.util.concurrent.atomic.AtomicBoolean upstreamCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        doReturn(Flux.concat(Flux.just("部分"), Flux.never()).doOnCancel(() -> upstreamCancelled.set(true)))
                .when(knowledgeRagService)
                .streamAnswer(eq(new CurrentActor(1L)), eq(307L), eq("wait"), isNull(), eq(false), any());

        Disposable subscription = messageService.createStream(actor, dto).subscribe();
        subscription.dispose();

        ArgumentCaptor<Message> assistantCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).updateById(assistantCaptor.capture());
        assertEquals("部分", assistantCaptor.getValue().getContent());
        assertTrue(upstreamCancelled.get());
        verify(transactionTemplate, times(2)).execute(any());
    }
}
