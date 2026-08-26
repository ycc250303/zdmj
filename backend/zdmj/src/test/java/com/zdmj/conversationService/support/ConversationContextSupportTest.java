package com.zdmj.conversationService.support;

import com.zdmj.conversationService.dto.MessageDTO;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumePersonalInfoDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void resolveUseSystemKnowledge_requestOverridesConfig() {
        Conversation conversation = new Conversation();
        conversation.setConfig(Map.of(ConversationContextSupport.CONFIG_USE_SYSTEM_KNOWLEDGE, false));
        MessageDTO dto = new MessageDTO();
        dto.setUseSystemKnowledge(true);

        assertTrue(ConversationContextSupport.resolveUseSystemKnowledge(dto, conversation));
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
        assertFalse(ConversationContextSupport.resolveUseSystemKnowledge(new MessageDTO(), new Conversation()));
    }
}
