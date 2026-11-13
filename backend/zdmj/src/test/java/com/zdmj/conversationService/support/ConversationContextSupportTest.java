package com.zdmj.conversationService.support;

import com.zdmj.conversationService.entity.Conversation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationContextSupportTest {

    @Test
    void freezeConfig_shouldDropUnknownKeysAndKeepRagIds() {
        Map<String, Object> frozen = ConversationContextSupport.freezeConfig(Map.of(
                "model", "gpt",
                ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, true,
                ConversationContextSupport.CONFIG_RAG_DOCUMENT_IDS, List.of(1, 2)));

        assertEquals(true, frozen.get(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE));
        assertEquals(List.of(1L, 2L), frozen.get(ConversationContextSupport.CONFIG_RAG_DOCUMENT_IDS));
        assertFalse(frozen.containsKey("model"));
    }

    @Test
    void freezeConfig_nullIncoming_shouldDefaultSystemKnowledgeOff() {
        Map<String, Object> frozen = ConversationContextSupport.freezeConfig(null);

        assertEquals(false, frozen.get(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE));
        assertFalse(frozen.containsKey(ConversationContextSupport.CONFIG_RAG_DOCUMENT_IDS));
    }

    @Test
    void resolveUseSystemKnowledge_readsConversationConfig() {
        Conversation conversation = new Conversation();
        conversation.setConfig(Map.of(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, true));

        assertTrue(ConversationContextSupport.resolveUseSystemKnowledge(conversation));
    }

    @Test
    void resolveUseSystemKnowledge_defaultFalse() {
        assertFalse(ConversationContextSupport.resolveUseSystemKnowledge(new Conversation()));
    }

    @Test
    void resolveRagDocumentIds_missingKey_shouldBeNull() {
        Conversation conversation = new Conversation();
        conversation.setConfig(Map.of(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, false));

        assertNull(ConversationContextSupport.resolveRagDocumentIds(conversation));
    }
}
