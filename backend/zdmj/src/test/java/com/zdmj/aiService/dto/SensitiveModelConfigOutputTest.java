package com.zdmj.aiService.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.aiService.entity.UserLlmConfig;

class SensitiveModelConfigOutputTest {

    @Test
    void generatedStringExcludesModelCredentials() throws Exception {
        var entity = new UserLlmConfig();
        entity.setApiKeyCiphertext("ciphertext-secret");
        assertFalse(entity.toString().contains("ciphertext-secret"));

        var mapper = new ObjectMapper();
        var request = mapper.readValue("""
                {"modelCode":"deepseek-flash","apiKey":"credential-secret"}
                """, UserLlmConfigRequest.class);
        assertFalse(request.toString().contains("credential-secret"));
        assertEquals("deepseek-flash", request.modelCode());
        assertEquals("credential-secret", request.apiKey());
    }
}
