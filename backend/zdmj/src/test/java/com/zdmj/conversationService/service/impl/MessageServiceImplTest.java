package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zdmj.common.ai.config.RagConfig;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.mapper.ConversationMapper;
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
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private ChatUtil chatUtil;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private KnowledgeRagService knowledgeRagService;
    @Mock
    private RagConfig ragConfig;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageService = spy(new MessageServiceImpl(
                chatUtil,
                messageMapper,
                conversationService,
                conversationMapper,
                knowledgeRagService,
                ragConfig));
        lenient().doReturn(2).when(conversationMapper).incrementMessageCountAndGet(anyLong(), anyLong(), anyInt());
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void getMessages_limitCapped100_shouldReturnCappedPage() {
        Conversation conversation = new Conversation();
        conversation.setId(201L);
        doReturn(conversation).when(conversationService).requireOwned(201L);
        Page<Message> mpPage = new Page<>(1, 100);
        mpPage.setRecords(List.of(new Message()));
        mpPage.setTotal(5);
        doReturn(mpPage).when(messageMapper).selectPageByConversationId(any(Page.class), eq(201L));

        PageDTO<MessageResponse> page = messageService.getMessagesByConversationId(201L, 1, 500);

        assertEquals(100, page.getLimit());
        assertEquals(1, page.getPage());
        assertEquals(5L, page.getTotal());
        assertEquals(1, page.getList().size());
        verify(messageMapper).selectPageByConversationId(any(Page.class), eq(201L));
    }

    @Test
    void getMessages_conversationAccessFailed_shouldPropagate() {
        doThrow(new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND))
                .when(conversationService).requireOwned(202L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> messageService.getMessagesByConversationId(202L, 1, 10));

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
        doReturn(conversation).when(conversationService).requireOwned(301L);
        doReturn(0).when(messageMapper).insert(any(Message.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.createStream(dto));

        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.MESSAGE_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).insert(any(Message.class));
        verify(conversationService, never()).updateById(any(Conversation.class));
    }

    @Test
    void login_createStream_successCompleted_shouldPersistAndEmitDelta() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(302L);
        dto.setMessage("hello");
        Conversation conversation = new Conversation();
        conversation.setId(302L);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).requireOwned(302L);
        doReturn(false).when(ragConfig).isEnabled();
        doReturn("title").when(chatUtil).chatOnce(anyLong(), anyString(), anyString(), any());
        doReturn(Flux.just("he", "llo")).when(chatUtil)
                .chatStreamInConversation(eq(1L), eq(302L), eq("hello"), anyString(), any());
        AtomicInteger insertTimes = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (insertTimes.incrementAndGet() == 2) {
                m.setId(900L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));

        List<ServerSentEvent<String>> events = messageService.createStream(dto).collectList().block();

        assertFalse(events == null);
        assertEquals(2, events.size());
        assertEquals("delta", events.get(0).event());
        assertEquals(true, Objects.requireNonNull(events.get(0).data()).contains("\"he\""));
        assertEquals("delta", events.get(1).event());
        assertEquals(true, Objects.requireNonNull(events.get(1).data()).contains("\"llo\""));
        ArgumentCaptor<Message> assistantCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).updateById(assistantCaptor.capture());
        assertEquals("hello", assistantCaptor.getValue().getContent());
    }

    @Test
    void login_createStream_secondInsertSaveFailed_shouldThrow9005() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(303L);
        dto.setMessage("q");
        Conversation conversation = new Conversation();
        conversation.setId(303L);
        conversation.setMessageCount(2);
        doReturn(conversation).when(conversationService).requireOwned(303L);
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
    void reset_createStream_streamError_shouldPersistPartialAndEmitError() {
        ChatStreamRequest dto = new ChatStreamRequest();
        dto.setConversationId(304L);
        dto.setMessage("ask");
        Conversation conversation = new Conversation();
        conversation.setId(304L);
        conversation.setMessageCount(1);
        doReturn(conversation).when(conversationService).requireOwned(304L);
        doReturn(false).when(ragConfig).isEnabled();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(901L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(1).when(messageMapper).updateById(any(Message.class));
        doReturn(Flux.concat(Flux.just("x"), Flux.error(new RuntimeException("boom"))))
                .when(chatUtil).chatStreamInConversation(eq(1L), eq(304L), eq("ask"), anyString(), any());

        assertThrows(RuntimeException.class, () -> messageService.createStream(dto).collectList().block());
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
        doReturn(conversation).when(conversationService).requireOwned(305L);
        doReturn(false).when(ragConfig).isEnabled();
        org.mockito.Mockito.doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getRole() == 2) {
                m.setId(902L);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));
        doReturn(Flux.just("ok")).when(chatUtil).chatStreamInConversation(eq(1L), eq(305L), eq("go"), anyString(), any());
        doReturn(0).when(messageMapper).updateById(any(Message.class));

        assertThrows(RuntimeException.class, () -> messageService.createStream(dto).collectList().block());
        verify(messageMapper).updateById(any(Message.class));
    }

    @Test
    void createStream_concurrentSameConversation_shouldAllocateUniqueSequentialSequences() throws Exception {
        Long conversationId = 400L;
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setMessageCount(0);
        doReturn(conversation).when(conversationService).requireOwned(conversationId);

        doReturn(false).when(ragConfig).isEnabled();
        doReturn("title-once").when(chatUtil).chatOnce(anyLong(), anyString(), anyString(), any());
        doReturn(Flux.just("ok")).when(chatUtil)
                .chatStreamInConversation(eq(1L), eq(conversationId), anyString(), anyString(), any());

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
            Future<List<ServerSentEvent<String>>> f1 = pool.submit(() -> {
                UserHolder.set(UserContext.of(1L, "u1"));
                try {
                    return messageService.createStream(dto1).collectList().block();
                } finally {
                    UserHolder.clear();
                }
            });
            Future<List<ServerSentEvent<String>>> f2 = pool.submit(() -> {
                UserHolder.set(UserContext.of(1L, "u1"));
                try {
                    return messageService.createStream(dto2).collectList().block();
                } finally {
                    UserHolder.clear();
                }
            });

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
                    .updateTitleByIdAndUserId(eq(conversationId), anyLong(), eq("title-once"));
            verify(messageMapper, atLeastOnce()).updateById(any(Message.class));
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
