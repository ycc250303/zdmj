package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.support.ConversationContextSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ChatMemory chatMemory;

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(conversationMapper, messageMapper, chatMemory);
    }

    @Test
    void createConversation_withDto_shouldReturnConversationAndMessageCountZero() {
        ConversationRequest dto = new ConversationRequest();
        dto.setConfig(Map.of("model", "gpt"));
        doAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(1L);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        ConversationResponse actual = conversationService.create(actor, dto);

        assertEquals(1L, actual.getId());
        assertEquals(1L, actual.getUserId());
        assertEquals(0, actual.getMessageCount());
        assertFalse(actual.getConfig().containsKey("model"));
        assertFalse(Boolean.TRUE.equals(actual.getConfig().get(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE)));
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void createConversation_saveFailed_shouldThrow9001() {
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.create(actor, new ConversationRequest()));

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

        assertEquals(1L, actual.getUserId());
        assertEquals(0, actual.getMessageCount());
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

        assertEquals(11L, out.getId());
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

    @Test
    void updateTitle_titleBlank_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateTitle(actor, 1L, "   "));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("会话标题不能为空", ex.getMessage());
        verify(conversationMapper, never()).updateTitleByIdAndUserId(any(), any(), any());
    }

    @Test
    void updateTitle_shouldTrimAndUpdate() {
        Conversation owned = new Conversation();
        owned.setId(15L);
        owned.setUserId(1L);
        owned.setMessageCount(4);
        when(conversationMapper.selectById(15L)).thenReturn(owned, refreshedTitle());
        when(conversationMapper.updateTitleByIdAndUserId(15L, 1L, "new title")).thenReturn(1);

        ConversationResponse out = conversationService.updateTitle(actor, 15L, "  new title  ");

        assertEquals("new title", out.getTitle());
        assertEquals(4, out.getMessageCount());
        verify(conversationMapper).updateTitleByIdAndUserId(15L, 1L, "new title");
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
        verify(conversationMapper, never()).updateTitleByIdAndUserId(any(), any(), any());
    }

    @Test
    void updateTitle_updateFailed_shouldThrow9004() {
        Conversation owned = new Conversation();
        owned.setId(17L);
        owned.setUserId(1L);
        when(conversationMapper.selectById(17L)).thenReturn(owned);
        when(conversationMapper.updateTitleByIdAndUserId(17L, 1L, "updated")).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateTitle(actor, 17L, "updated"));

        assertEquals(ErrorCode.CONVERSATION_UPDATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_UPDATE_FAILED.getMessage(), ex.getMessage());
        verify(conversationMapper).updateTitleByIdAndUserId(17L, 1L, "updated");
    }

    @Test
    void removeFailed_whenDelete_shouldThrow9002() {
        Conversation owned = new Conversation();
        owned.setId(21L);
        owned.setUserId(1L);
        when(conversationMapper.selectById(21L)).thenReturn(owned);
        when(messageMapper.delete(any(Wrapper.class))).thenReturn(0);
        when(conversationMapper.delete(any(Wrapper.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.delete(actor, 21L));

        assertEquals(ErrorCode.CONVERSATION_DELETE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_DELETE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).delete(any(Wrapper.class));
        verify(chatMemory).clear("21");
        verify(conversationMapper).delete(any(Wrapper.class));
    }

    @Test
    void delete_shouldClearMessagesAndChatMemory() {
        Conversation owned = new Conversation();
        owned.setId(24L);
        owned.setUserId(1L);
        when(conversationMapper.selectById(24L)).thenReturn(owned);
        when(messageMapper.delete(any(Wrapper.class))).thenReturn(3);
        when(conversationMapper.delete(any(Wrapper.class))).thenReturn(1);

        conversationService.delete(actor, 24L);

        verify(messageMapper).delete(any(Wrapper.class));
        verify(chatMemory).clear("24");
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
        verify(chatMemory, never()).clear(any());
    }

    @Test
    void updateConfig_whenIdle_shouldSanitizeAndUpdate() {
        Conversation owned = new Conversation();
        owned.setId(23L);
        owned.setUserId(1L);
        owned.setMessageCount(0);
        owned.setConfig(new HashMap<>(Map.of("useSystemKnowledge", false)));
        Conversation refreshed = new Conversation();
        refreshed.setId(23L);
        refreshed.setUserId(1L);
        refreshed.setMessageCount(0);
        refreshed.setConfig(Map.of(
                "useSystemKnowledge", true,
                "ragDocumentIds", List.of(1L, 2L)));
        when(conversationMapper.selectById(23L)).thenReturn(owned, refreshed);
        when(conversationMapper.updateConfigByIdAndUserId(eq(23L), eq(1L), any())).thenReturn(1);

        ConversationResponse out = conversationService.updateConfig(actor, 23L, Map.of(
                "ragDocumentIds", List.of(1L, 2L),
                "useSystemKnowledge", true,
                "model", "gpt"));

        assertTrue(Boolean.TRUE.equals(out.getConfig().get("useSystemKnowledge")));
        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(conversationMapper).updateConfigByIdAndUserId(eq(23L), eq(1L), configCaptor.capture());
        assertFalse(configCaptor.getValue().containsKey("model"));
    }

    @Test
    void updateConfig_afterFirstMessage_shouldThrow9006() {
        Conversation owned = new Conversation();
        owned.setId(25L);
        owned.setUserId(1L);
        owned.setMessageCount(2);
        when(conversationMapper.selectById(25L)).thenReturn(owned);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> conversationService.updateConfig(actor, 25L, Map.of("useSystemKnowledge", true)));

        assertEquals(ErrorCode.CONVERSATION_CONFIG_LOCKED.getCode(), ex.getCode());
        verify(conversationMapper, never()).updateConfigByIdAndUserId(any(), any(), any());
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
