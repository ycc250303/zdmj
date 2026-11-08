package com.zdmj.aiService.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.zdmj.aiService.entity.UserLlmConfig;

class SensitiveModelConfigOutputTest {

    @Test
    void generatedStringExcludesModelCredentials() throws Exception {
        Object[][] fields = {{UserLlmConfig.class, "apiKeyCiphertext"},
                {UserLlmConfigRequest.class, "apiKey"}, {UserLlmConnectionTestRequest.class, "apiKey"}};
        for (Object[] entry : fields) {
            Class<?> type = (Class<?>) entry[0];
            String name = (String) entry[1];
            Object object = type.getConstructor().newInstance();
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, "credential-secret");
            assertFalse(object.toString().contains("credential-secret"), type.getSimpleName() + "." + name);
        }
    }
}
