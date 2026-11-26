package com.zdmj.conversationService.service.impl;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    private final CurrentActor actor = new CurrentActor(1L);

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(conversationMapper, messageMapper, events, java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void createConversation_withDto_shouldReturnConversationAndMessageCountZero() {
        ConversationRequest dto = new ConversationRequest(Map.of("model", "gpt", "useSystemKnowledge", true, "ragDocumentIds", List.of(1, 2)));

        doAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(1L);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        ConversationResponse actual = conversationService.create(actor, dto);

        assertEquals(1L, actual.id());
        assertEquals(1L, actual.userId());
        assertEquals(0, actual.messageCount());
        assertFalse(actual.config().containsKey("model"));
        assertEquals(true, actual.config().get("useSystemKnowledge"));
        assertEquals(List.of(1L, 2L), actual.config().get("ragDocumentIds"));
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void createConversation_saveFailed_shouldThrow9001() {
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.create(actor, new ConversationRequest(null)));

        assertEquals(ErrorCode.CONVERSATION_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void register_createConversation_withNullDto_shouldDefaultMessageCountZero() {
        doAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(2L);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        ConversationResponse actual = conversationService.create(actor, null);

        assertEquals(1L, actual.userId());
        assertEquals(0, actual.messageCount());
        assertEquals(Map.of("useSystemKnowledge", false), actual.config());
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void getById_idNull_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.getById(actor, null));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("会话ID不能为空", ex.getMessage());
        verify(conversationMapper, never()).selectById(any());
    }

    @Test
    void getById_dbHitAuthorized_shouldReturnConversation() {
        Conversation db = new Conversation();
        db.setId(11L);
        db.setUserId(1L);
        when(conversationMapper.selectById(11L)).thenReturn(db);

        ConversationResponse out = conversationService.getById(actor, 11L);

        assertEquals(11L, out.id());
        verify(conversationMapper).selectById(11L);
    }

    @Test
    void getById_noPermission_shouldThrow1003() {
        Conversation other = new Conversation();
        other.setId(12L);
        other.setUserId(2L);
        when(conversationMapper.selectById(12L)).thenReturn(other);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.getById(actor, 12L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(conversationMapper).selectById(12L);
    }

    @Test
    void login_getById_notFound_shouldThrow9003() {
        when(conversationMapper.selectById(13L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.getById(actor, 13L));

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getMessage(), ex.getMessage());
        verify(conversationMapper).selectById(13L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"标", "😀"})
    void 手工标题超过容量_返回参数错误并且不访问数据库(String unit) {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> conversationService.updateTitle(actor, 1L, unit.repeat(256)));
        assertEquals(1001, failure.getCode());
        org.mockito.Mockito.verifyNoInteractions(conversationMapper);
    }

    @Test
    void updateTitle_titleBlank_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateTitle(actor, 1L, "   "));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("会话标题不能为空", ex.getMessage());
        verify(conversationMapper, never()).updateTitleByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void updateTitle_shouldTrimAndUpdate() {
        Conversation owned = new Conversation();
        owned.setId(15L);
        owned.setUserId(1L);
        owned.setMessageCount(4);
        when(conversationMapper.selectById(15L)).thenReturn(owned, refreshedTitle());
        when(conversationMapper.updateTitleByIdAndUserId(eq(15L), eq(1L), eq("new title"), any(LocalDateTime.class))).thenReturn(1);

        ConversationResponse out = conversationService.updateTitle(actor, 15L, "  new title  ");

        assertEquals("new title", out.title());
        assertEquals(4, out.messageCount());
        verify(conversationMapper).updateTitleByIdAndUserId(eq(15L), eq(1L), eq("new title"), any(LocalDateTime.class));
    }

    @Test
    void updateTitle_noPermission_shouldThrow1003() {
        Conversation other = new Conversation();
        other.setId(16L);
        other.setUserId(2L);
        when(conversationMapper.selectById(16L)).thenReturn(other);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateTitle(actor, 16L, "new"));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(conversationMapper, never()).updateTitleByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void updateTitle_updateFailed_shouldThrow9004() {
        Conversation owned = new Conversation();
        owned.setId(17L);
        owned.setUserId(1L);
        when(conversationMapper.selectById(17L)).thenReturn(owned);
        when(conversationMapper.updateTitleByIdAndUserId(eq(17L), eq(1L), eq("updated"), any(LocalDateTime.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateTitle(actor, 17L, "updated"));

        assertEquals(ErrorCode.CONVERSATION_UPDATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_UPDATE_FAILED.getMessage(), ex.getMessage());
        verify(conversationMapper).updateTitleByIdAndUserId(eq(17L), eq(1L), eq("updated"), any(LocalDateTime.class));
    }

    @Test
    void removeFailed_whenDelete_shouldThrow9002() {
        Conversation owned = new Conversation();
        owned.setId(21L);
        owned.setUserId(1L);
        when(conversationMapper.selectById(21L)).thenReturn(owned);
        when(conversationMapper.selectOwnedForUpdate(21L, 1L)).thenReturn(owned);
        when(messageMapper.delete(any(Wrapper.class))).thenReturn(0);
        when(conversationMapper.delete(any(Wrapper.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.delete(actor, 21L));

        assertEquals(ErrorCode.CONVERSATION_DELETE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_DELETE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).delete(any(Wrapper.class));
        verify(conversationMapper).delete(any(Wrapper.class));
    }

    @Test
    void delete_shouldLockConversationAndClearMessages() {
        Conversation owned = new Conversation();
        owned.setId(24L);
        owned.setUserId(1L);
        when(conversationMapper.selectById(24L)).thenReturn(owned);
        when(messageMapper.delete(any(Wrapper.class))).thenReturn(3);
        when(conversationMapper.delete(any(Wrapper.class))).thenReturn(1);

        when(conversationMapper.selectOwnedForUpdate(24L, 1L)).thenReturn(owned);
        conversationService.delete(actor, 24L);
        verify(events).publishEvent(new ConversationServiceImpl.Deleted(actor, 24L));

        verify(conversationMapper).selectOwnedForUpdate(24L, 1L);
        verify(messageMapper).delete(any(Wrapper.class));
        verify(conversationMapper).delete(any(Wrapper.class));
    }

    @Test
    void update_delete_noPermission_shouldThrow1003() {
        Conversation other = new Conversation();
        other.setId(22L);
        other.setUserId(2L);
        when(conversationMapper.selectById(22L)).thenReturn(other);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.delete(actor, 22L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(conversationMapper, never()).delete(any(Wrapper.class));
        verify(messageMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void updateConfig_whenIdle_shouldSanitizeAndUpdate() {
        Conversation owned = new Conversation();
        owned.setId(23L);
        owned.setUserId(1L);
        owned.setMessageCount(0);
        owned.setConfig(new HashMap<>(Map.of("useSystemKnowledge", false)));
        Conversation locked = new Conversation();
        locked.setId(23L);
        locked.setUserId(1L);
        locked.setMessageCount(0);
        locked.setConfig(Map.of("useSystemKnowledge", true));
        Conversation refreshed = new Conversation();
        refreshed.setId(23L);
        refreshed.setUserId(1L);
        refreshed.setMessageCount(0);
        refreshed.setConfig(Map.of(
                "useSystemKnowledge", true,
                "ragDocumentIds", List.of(1L, 2L)));
        when(conversationMapper.selectById(23L)).thenReturn(owned, refreshed);
        when(conversationMapper.selectOwnedForUpdate(23L, 1L)).thenReturn(locked);
        when(conversationMapper.updateConfigByIdAndUserId(eq(23L), eq(1L), any(), any(LocalDateTime.class))).thenReturn(1);

        ConversationResponse out = conversationService.updateConfig(actor, 23L, Map.of(
                "ragDocumentIds", List.of(1L, 2L),
                "model", "gpt"));

        assertTrue(Boolean.TRUE.equals(out.config().get("useSystemKnowledge")));
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(conversationMapper).updateConfigByIdAndUserId(eq(23L), eq(1L), configCaptor.capture(), any(LocalDateTime.class));
        assertEquals(Map.of("useSystemKnowledge", true, "ragDocumentIds", List.of(1L, 2L)), configCaptor.getValue());
        assertEquals(Map.of("useSystemKnowledge", false), owned.getConfig());
        var order = org.mockito.Mockito.inOrder(conversationMapper);
        order.verify(conversationMapper).selectById(23L);
        order.verify(conversationMapper).selectOwnedForUpdate(23L, 1L);
        order.verify(conversationMapper).updateConfigByIdAndUserId(eq(23L), eq(1L), any(), any(LocalDateTime.class));
        order.verify(conversationMapper).selectById(23L);
    }

    @Test
    void updateConfig_afterFirstMessage_shouldThrow9006() {
        Conversation owned = new Conversation();
        owned.setId(25L);
        owned.setUserId(1L);
        owned.setMessageCount(0);
        Conversation locked = new Conversation();
        locked.setMessageCount(2);
        when(conversationMapper.selectById(25L)).thenReturn(owned);
        when(conversationMapper.selectOwnedForUpdate(25L, 1L)).thenReturn(locked);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateConfig(actor, 25L, Map.of("useSystemKnowledge", true)));

        assertEquals(ErrorCode.CONVERSATION_CONFIG_LOCKED.getCode(), ex.getCode());
        verify(conversationMapper, never()).updateConfigByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void updateConfig_deletedBeforeLock_shouldReturnNotFound() {
        Conversation owned = new Conversation();
        owned.setUserId(actor.userId());
        when(conversationMapper.selectById(23L)).thenReturn(owned);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> conversationService.updateConfig(actor, 23L, Map.of("useSystemKnowledge", true)));

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), failure.getCode());
        verify(conversationMapper).selectOwnedForUpdate(23L, 1L);
        verify(conversationMapper, never()).updateConfigByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 2})
    void updateConfig_unexpectedRowsAfterLock_shouldFailImmediately(int rows) {
        Conversation owned = new Conversation();
        owned.setUserId(actor.userId());
        when(conversationMapper.selectById(23L)).thenReturn(owned);
        when(conversationMapper.selectOwnedForUpdate(23L, 1L)).thenReturn(owned);
        when(conversationMapper.updateConfigByIdAndUserId(eq(23L), eq(1L), any(), any(LocalDateTime.class))).thenReturn(rows);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> conversationService.updateConfig(actor, 23L, Map.of("useSystemKnowledge", true)));

        assertTrue(failure.getMessage().contains("conversationId=23"));
        assertTrue(failure.getMessage().contains("rows=" + rows));
        verify(conversationMapper).selectById(23L);
    }

    @Test
    void invalidRequestConfig_shouldFailBeforeWriting() {
        BusinessException createError = assertThrows(BusinessException.class,
                () -> conversationService.create(actor, new ConversationRequest(Map.of("ragDocumentIds", List.of(1.5)))));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), createError.getCode());
        verify(conversationMapper, never()).insert(any(Conversation.class));

        Conversation owned = new Conversation();
        owned.setId(23L);
        owned.setUserId(1L);
        owned.setConfig(Map.of("useSystemKnowledge", true, "ragDocumentIds", List.of(2L)));
        when(conversationMapper.selectById(23L)).thenReturn(owned);
        when(conversationMapper.selectOwnedForUpdate(23L, 1L)).thenReturn(owned);
        BusinessException updateError = assertThrows(BusinessException.class,
                () -> conversationService.updateConfig(actor, 23L, Map.of("useSystemKnowledge", "true")));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), updateError.getCode());
        verify(conversationMapper, never()).updateConfigByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
        assertEquals(Map.of("useSystemKnowledge", true, "ragDocumentIds", List.of(2L)), owned.getConfig());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"useSystemKnowledge", "ragDocumentIds"})
    void updateConfig_invalidStoredConfig_shouldFailEvenWhenRequestReplacesValue(String key) {
        Conversation owned = new Conversation();
        owned.setId(23L);
        owned.setUserId(1L);
        owned.setConfig(Map.of(key, "invalid"));
        when(conversationMapper.selectById(23L)).thenReturn(owned);
        when(conversationMapper.selectOwnedForUpdate(23L, 1L)).thenReturn(owned);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> conversationService.updateConfig(actor, 23L,
                        Map.of("useSystemKnowledge", false, "ragDocumentIds", List.of())));
        assertTrue(error.getMessage().contains("conversationId=23"));
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        verify(conversationMapper, never()).updateConfigByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
        assertEquals(Map.of(key, "invalid"), owned.getConfig());
    }

    @ParameterizedTest
    @MethodSource("invalidConfig")
    void invalidConfig_shouldRejectRequestAndReportStoredConfigAsSystemFailure(String key, Object value, String detail) {
        Map<String, Object> config = Map.of(key, value);
        ConversationRequest request = new ConversationRequest(config);
        IllegalArgumentException fieldError = assertThrows(IllegalArgumentException.class, () -> {
            if (key.equals("ragDocumentIds")) {
                request.ragDocumentIds();
            } else {
                request.useSystemKnowledge();
            }
        });
        assertEquals(detail, fieldError.getMessage());
        BusinessException requestError = assertThrows(BusinessException.class,
                () -> conversationService.create(actor, request));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), requestError.getCode());
        assertEquals(detail, requestError.getMessage());
        assertInstanceOf(IllegalArgumentException.class, requestError.getCause());
        verify(conversationMapper, never()).insert(any(Conversation.class));

        Conversation conversation = new Conversation();
        conversation.setId(23L);
        conversation.setUserId(actor.userId());
        conversation.setConfig(config);
        when(conversationMapper.selectById(23L)).thenReturn(conversation);
        when(conversationMapper.selectOwnedForUpdate(23L, 1L)).thenReturn(conversation);
        IllegalStateException storedError = assertThrows(IllegalStateException.class,
                () -> conversationService.updateConfig(actor, 23L, Map.of("model", "ignored")));
        assertTrue(storedError.getMessage().contains("conversationId=23"));
        assertInstanceOf(IllegalArgumentException.class, storedError.getCause());
        assertEquals(detail, storedError.getCause().getMessage());
        verify(conversationMapper, never()).updateConfigByIdAndUserId(any(), any(), any(), any(LocalDateTime.class));
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof BigInteger) {
            assertInstanceOf(ArithmeticException.class, fieldError.getCause());
            assertInstanceOf(ArithmeticException.class, requestError.getCause().getCause());
            assertInstanceOf(ArithmeticException.class, storedError.getCause().getCause());
        }
    }

    static Stream<Arguments> invalidConfig() {
        Stream<Arguments> invalidArrays = Stream.of("10", 10, true, Map.of("id", 10))
                .map(value -> Arguments.of("ragDocumentIds", value, "ragDocumentIds 必须为数组或 null"));
        Stream<Arguments> invalidElements = Stream.of("10", 10.0d, 10.0f, new BigDecimal("10.0"), true, List.of(10), Map.of())
                .map(value -> Arguments.of("ragDocumentIds", List.of(1, value), "ragDocumentIds[1] 必须为整数"));
        Stream<Arguments> invalidValues = Stream.of(
                Arguments.of("ragDocumentIds", Arrays.asList(1, null), "ragDocumentIds[1] 必须为整数"),
                Arguments.of("ragDocumentIds", List.of(0), "ragDocumentIds[0] 必须为正整数"),
                Arguments.of("ragDocumentIds", List.of(-1L), "ragDocumentIds[0] 必须为正整数"),
                Arguments.of("ragDocumentIds", List.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)), "ragDocumentIds[0] 超出编号取值范围"),
                Arguments.of("ragDocumentIds", List.of(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)), "ragDocumentIds[0] 超出编号取值范围"));
        Stream<Arguments> invalidFlags = Stream.of("true", "false", "1", "", 0, 1, 1.0, List.of(), Map.of())
                .map(value -> Arguments.of("useSystemKnowledge", value, "useSystemKnowledge 必须为布尔值"));
        return Stream.of(invalidArrays, invalidElements, invalidValues, invalidFlags).flatMap(stream -> stream);
    }

    @Test
    void integerIds_shouldKeepOrderDuplicatesAndLongBoundary() {
        List<Object> raw = List.of((byte) 1, (short) 2, 3, 4L, BigInteger.valueOf(Long.MAX_VALUE), 3);
        List<Long> expected = List.of(1L, 2L, 3L, 4L, Long.MAX_VALUE, 3L);
        ConversationRequest request = new ConversationRequest(Map.of("ragDocumentIds", raw));
        assertEquals(expected, request.ragDocumentIds());
        doAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(1L);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));
        assertEquals(expected, conversationService.create(actor, request).config().get("ragDocumentIds"));
    }

    @Test
    void explicitNullAndEmptyIds_shouldPreserveRetrievalSemantics() {
        Map<String, Object> config = new HashMap<>();
        config.put("ragDocumentIds", null);
        config.put("useSystemKnowledge", null);
        ConversationRequest request = new ConversationRequest(config);
        assertNull(request.ragDocumentIds());
        assertFalse(request.useSystemKnowledge());
        assertNull(new ConversationRequest(null).ragDocumentIds());
        assertFalse(new ConversationRequest(null).useSystemKnowledge());
        assertNull(new ConversationRequest(Map.of()).ragDocumentIds());
        assertFalse(new ConversationRequest(Map.of()).useSystemKnowledge());
        config.put("ragDocumentIds", List.of());
        assertEquals(List.of(), new ConversationRequest(config).ragDocumentIds());
    }

    private static Conversation refreshedTitle() {
        Conversation refreshed = new Conversation();
        refreshed.setId(15L);
        refreshed.setUserId(1L);
        refreshed.setTitle("new title");
        refreshed.setMessageCount(4);
        return refreshed;
    }
}
