package com.zdmj.conversationService.support;

import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumePersonalInfoDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationContextSupportTest {

    @Test
    void formatResume_shouldIncludePersonalAndEducation() {
        ResumeContentResponse resume = new ResumeContentResponse();
        ResumePersonalInfoDTO personal = new ResumePersonalInfoDTO();
        personal.setName("张三");
        personal.setPreferredWorkCity("上海");
        resume.setPersonalInfo(personal);
        EducationRequest edu = new EducationRequest();
        edu.setSchool("某某大学");
        edu.setMajor("软件工程");
        resume.setEducations(List.of(edu));

        String text = ConversationContextSupport.formatResume(resume);

        assertTrue(text.contains("张三"));
        assertTrue(text.contains("上海"));
        assertTrue(text.contains("某某大学"));
    }

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
    void buildChatPromptVars_shouldReadResumeFromContext() {
        Conversation conversation = new Conversation();
        conversation.setContext(List.of(Map.of(
                "type", ConversationContextSupport.CONTEXT_TYPE_RESUME,
                "content", "姓名：李四")));

        Map<String, Object> vars = ConversationContextSupport.buildChatPromptVars(conversation);

        assertEquals("姓名：李四", vars.get(ConversationContextSupport.PROMPT_VAR_RESUME_CONTEXT));
    }

    @Test
    void buildChatPromptVars_emptyResume_shouldUseHint() {
        Conversation conversation = new Conversation();
        conversation.setContext(List.of());

        Map<String, Object> vars = ConversationContextSupport.buildChatPromptVars(conversation);

        assertTrue(String.valueOf(vars.get(ConversationContextSupport.PROMPT_VAR_RESUME_CONTEXT)).contains("尚未填写"));
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
