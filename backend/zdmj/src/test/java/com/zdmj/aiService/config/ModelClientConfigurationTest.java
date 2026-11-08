package com.zdmj.aiService.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.zdmj.aiService.enums.ModelCode;

class ModelClientConfigurationTest {

    @Test
    void buildUserModel_shouldDisableThinkingForDeepSeekAndQwen() {
        ModelClientConfiguration configuration = new ModelClientConfiguration();

        assertDeepSeekThinkingDisabled(configuration.buildUserModel(ModelCode.DEEPSEEK_FLASH, "sk-test"),
                "deepseek-flash");
        assertDeepSeekThinkingDisabled(configuration.buildUserModel(ModelCode.DEEPSEEK_PRO, "sk-test"),
                "deepseek-v4-pro");
        assertQwenThinkingDisabled(configuration.buildUserModel(ModelCode.QWEN_PLUS, "sk-test"));
    }

    private static void assertDeepSeekThinkingDisabled(ChatModel model, String apiModelName) {
        OpenAiChatOptions options = optionsOf(model);
        assertEquals(apiModelName, options.getModel());
        assertEquals("disabled", thinkingType(options));
        assertFalse(options.getExtraBody().containsKey("enable_thinking"));
    }

    private static void assertQwenThinkingDisabled(ChatModel model) {
        OpenAiChatOptions options = optionsOf(model);
        assertEquals("qwen3.8-flash", options.getModel());
        assertEquals(Boolean.FALSE, options.getExtraBody().get("enable_thinking"));
        assertFalse(options.getExtraBody().containsKey("thinking"));
    }

    @SuppressWarnings("unchecked")
    private static String thinkingType(OpenAiChatOptions options) {
        Map<String, Object> thinking = (Map<String, Object>) options.getExtraBody().get("thinking");
        return String.valueOf(thinking.get("type"));
    }

    private static OpenAiChatOptions optionsOf(ChatModel model) {
        return (OpenAiChatOptions) ((OpenAiChatModel) model).getDefaultOptions();
    }
}
