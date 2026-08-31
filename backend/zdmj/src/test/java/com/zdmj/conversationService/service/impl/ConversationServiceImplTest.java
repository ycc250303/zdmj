package com.zdmj.conversationService.service.impl;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.support.ConversationContextSupport;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumePersonalInfoDTO;
import com.zdmj.resumeService.service.ResumeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ChatMemory chatMemory;
    @Mock
    private ResumeService resumeService;

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = spy(new ConversationServiceImpl(conversationMapper, messageMapper, chatMemory, resumeService));
        lenient().when(resumeService.getMyResumeContent()).thenReturn(new ResumeContentResponse());
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void createConversation_withDto_shouldReturnConversationAndMessageCountZero() {
        ConversationRequest dto = new ConversationRequest();
        dto.setConfig(Map.of("model", "gpt"));
        doReturn(true).when(conversationService).save(any(Conversation.class));

        ConversationResponse out = conversationService.create(dto);
        ConversationResponse actual = Objects.requireNonNull(out);

        assertEquals(1L, actual.getUserId());
        assertEquals(0, actual.getMessageCount());
        assertEquals("gpt", actual.getConfig().get("model"));
        assertFalse(Boolean.TRUE.equals(actual.getConfig().get(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE)));
        verify(conversationService).save(any(Conversation.class));
    }

    @Test
    void createConversation_shouldInjectResumeContext() {
        ResumeContentResponse resume = new ResumeContentResponse();
        ResumePersonalInfoDTO personal = new ResumePersonalInfoDTO();
        personal.setName("测试用户");
        resume.setPersonalInfo(personal);
        when(resumeService.getMyResumeContent()).thenReturn(resume);
        doReturn(true).when(conversationService).save(any(Conversation.class));

        ConversationResponse out = conversationService.create(new ConversationRequest());
        ConversationResponse actual = Objects.requireNonNull(out);

        assertTrue(actual.getContext().stream()
                .anyMatch(item -> ConversationContextSupport.CONTEXT_TYPE_RESUME.equals(item.get("type"))));
        assertTrue(String.valueOf(actual.getContext().get(0).get("content")).contains("测试用户"));
        verify(resumeService).getMyResumeContent();
    }

    @Test
    void createConversation_saveFailed_shouldThrow9001() {
        doReturn(false).when(conversationService).save(any(Conversation.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.create(new ConversationRequest()));

        assertEquals(ErrorCode.CONVERSATION_CREATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_CREATE_FAILED.getMessage(), ex.getMessage());
        verify(conversationService).save(any(Conversation.class));
    }

    @Test
    void register_createConversation_withNullDto_shouldDefaultMessageCountZero() {
        doReturn(true).when(conversationService).save(any(Conversation.class));

        ConversationResponse out = conversationService.create(null);
        ConversationResponse actual = Objects.requireNonNull(out);

        assertEquals(1L, actual.getUserId());
        assertEquals(0, actual.getMessageCount());
        verify(conversationService).save(any(Conversation.class));
    }

    @Test
    void getById_idNull_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.getById(null));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("会话ID不能为空", ex.getMessage());
        verify(conversationMapper, never()).selectById(any());
    }

    @Test
    void getById_dbHitAuthorized_shouldReturnConversation() {
        Conversation db = new Conversation();
        db.setId(11L);
        db.setUserId(1L);
        doReturn(db).when(conversationMapper).selectById(11L);

        ConversationResponse out = conversationService.getById(11L);

        assertEquals(11L, out.getId());
        verify(conversationMapper).selectById(11L);
    }

    @Test
    void getById_noPermission_shouldThrow1003() {
        Conversation other = new Conversation();
        other.setId(12L);
        other.setUserId(2L);
        doReturn(other).when(conversationMapper).selectById(12L);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.getById(12L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(conversationMapper).selectById(12L);
    }

    @Test
    void login_getById_notFound_shouldThrow9003() {
        doReturn(null).when(conversationMapper).selectById(13L);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.getById(13L));

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND.getMessage(), ex.getMessage());
        verify(conversationMapper).selectById(13L);
    }

    @Test
    void updateTitle_titleBlank_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.updateTitle(1L, "   "));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        assertEquals("会话标题不能为空", ex.getMessage());
        verify(conversationService, never()).updateById(any(Conversation.class));
    }

    @Test
    void updateTitle_shouldTrimAndUpdate() {
        Conversation owned = new Conversation();
        owned.setId(15L);
        owned.setUserId(1L);
        owned.setMessageCount(4);
        doReturn(owned).when(conversationService).requireOwned(15L);
        doReturn(1).when(conversationMapper).updateTitleByIdAndUserId(15L, 1L, "new title");
        Conversation refreshed = new Conversation();
        refreshed.setId(15L);
        refreshed.setUserId(1L);
        refreshed.setTitle("new title");
        refreshed.setMessageCount(4);
        doReturn(refreshed).when(conversationMapper).selectById(15L);

        ConversationResponse out = conversationService.updateTitle(15L, "  new title  ");

        assertEquals("new title", out.getTitle());
        assertEquals(4, out.getMessageCount());
        verify(conversationMapper).updateTitleByIdAndUserId(15L, 1L, "new title");
        verify(conversationMapper).selectById(15L);
        verify(conversationService, never()).updateById(any(Conversation.class));
    }

    @Test
    void updateTitle_noPermission_shouldThrow1003() {
        Conversation other = new Conversation();
        other.setId(16L);
        other.setUserId(2L);
        doReturn(other).when(conversationService).requireOwned(16L);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.updateTitle(16L, "new"));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(conversationService, never()).updateById(any(Conversation.class));
    }

    @Test
    void updateTitle_updateFailed_shouldThrow9004() {
        Conversation owned = new Conversation();
        owned.setId(17L);
        owned.setUserId(1L);
        doReturn(owned).when(conversationService).requireOwned(17L);
        doReturn(0).when(conversationMapper).updateTitleByIdAndUserId(17L, 1L, "updated");

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.updateTitle(17L, "updated"));

        assertEquals(ErrorCode.CONVERSATION_UPDATE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_UPDATE_FAILED.getMessage(), ex.getMessage());
        verify(conversationMapper).updateTitleByIdAndUserId(17L, 1L, "updated");
    }

    @Test
    void removeFailed_whenDelete_shouldThrow9002() {
        Conversation owned = new Conversation();
        owned.setId(21L);
        owned.setUserId(1L);
        doReturn(owned).when(conversationService).requireOwned(21L);
        doReturn(false).when(conversationService).removeById(21L);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.delete(21L));

        assertEquals(ErrorCode.CONVERSATION_DELETE_FAILED.getCode(), ex.getCode());
        assertEquals(ErrorCode.CONVERSATION_DELETE_FAILED.getMessage(), ex.getMessage());
        verify(messageMapper).delete(any());
        verify(chatMemory).clear("21");
        verify(conversationService).removeById(21L);
    }

    @Test
    void delete_shouldClearMessagesAndChatMemory() {
        Conversation owned = new Conversation();
        owned.setId(24L);
        owned.setUserId(1L);
        doReturn(owned).when(conversationService).requireOwned(24L);
        doReturn(true).when(conversationService).removeById(24L);
        doReturn(3).when(messageMapper).delete(any());

        conversationService.delete(24L);

        verify(messageMapper).delete(any());
        verify(chatMemory).clear("24");
        verify(conversationService).removeById(24L);
    }

    @Test
    void update_delete_noPermission_shouldThrow1003() {
        Conversation other = new Conversation();
        other.setId(22L);
        other.setUserId(2L);
        doReturn(other).when(conversationService).requireOwned(22L);

        BusinessException ex = assertThrows(BusinessException.class, () -> conversationService.delete(22L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(conversationService, never()).removeById(22L);
        verify(messageMapper, never()).delete(any());
        verify(chatMemory, never()).clear(any());
    }

    @Test
    void updateConfig_shouldMergeConfigWithoutUsingUpdateById() {
        Conversation owned = new Conversation();
        owned.setId(23L);
        owned.setUserId(1L);
        owned.setMessageCount(0);
        owned.setConfig(new HashMap<>(Map.of("useSystemKnowledge", false)));
        doReturn(owned).when(conversationService).requireOwned(23L);
        doReturn(1).when(conversationMapper).updateConfigByIdAndUserId(eq(23L), eq(1L), any());
        Conversation refreshed = new Conversation();
        refreshed.setId(23L);
        refreshed.setUserId(1L);
        refreshed.setMessageCount(6);
        refreshed.setConfig(Map.of(
                "useSystemKnowledge", true,
                "ragDocumentIds", List.of(1L, 2L)));
        doReturn(refreshed).when(conversationMapper).selectById(23L);

        ConversationResponse out = conversationService.updateConfig(23L, Map.of(
                "ragDocumentIds", List.of(1L, 2L),
                "useSystemKnowledge", true));

        assertEquals(6, out.getMessageCount());
        assertTrue(Boolean.TRUE.equals(out.getConfig().get("useSystemKnowledge")));
        verify(conversationMapper).updateConfigByIdAndUserId(eq(23L), eq(1L), any());
        verify(conversationService, never()).updateById(any(Conversation.class));
    }
}
