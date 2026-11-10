package com.zdmj.aiService.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.zdmj.aiService.enums.ModelCode;

class ModelClientConfigurationTest {

    @ParameterizedTest
    @EnumSource(ModelCode.class)
    void buildProbe_shouldUseCatalogAndLimitResponse(ModelCode model) {
        verifyRequest(model, true);
    }

    @ParameterizedTest
    @EnumSource(ModelCode.class)
    void buildUserModel_shouldUseCatalogWithoutProbeLimits(ModelCode model) {
        verifyRequest(model, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void synchronousRequest_whenProviderFails_shouldStopWithoutRetry(boolean probe) {
        ModelClientConfiguration configuration = new ModelClientConfiguration();
        OpenAiChatModel client = (OpenAiChatModel) (probe
                ? configuration.buildProbe(ModelCode.DEEPSEEK_FLASH, "sk-test")
                : configuration.buildUserModel(ModelCode.DEEPSEEK_FLASH, "sk-test"));
        OpenAiApi api = (OpenAiApi) ReflectionTestUtils.getField(client, "openAiApi");
        RestClient.Builder builder = ((RestClient) ReflectionTestUtils.getField(api, "restClient")).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(api, "restClient", builder.build());
        server.expect(requestTo(ModelCode.DEEPSEEK_FLASH.baseUrl() + "/v1/chat/completions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        // 后续请求一旦发生即违反期望；所有请求均由本地替身处理。
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.ai.retry.TransientAiException.class,
                () -> client.call(new Prompt("ping")));
        server.verify();
    }

    private static void verifyRequest(ModelCode model, boolean probe) {
        ModelClientConfiguration configuration = new ModelClientConfiguration();
        OpenAiChatModel client = (OpenAiChatModel) (probe
                ? configuration.buildProbe(model, "sk-test") : configuration.buildUserModel(model, "sk-test"));
        OpenAiChatOptions options = (OpenAiChatOptions) client.getDefaultOptions();
        assertEquals(model.apiModelName(), options.getModel());
        if (probe) {
            assertEquals(1, options.getMaxTokens());
            assertEquals(0.0, options.getTemperature());
        } else {
            assertNull(options.getMaxTokens());
        }

        // 仅替换 HTTP 传输，验证生产客户端组装的地址、鉴权与请求体，禁止访问供应商。
        OpenAiApi api = (OpenAiApi) ReflectionTestUtils.getField(client, "openAiApi");
        RestClient.Builder builder = ((RestClient) ReflectionTestUtils.getField(api, "restClient")).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(api, "restClient", builder.build());
        ResponseActions expectation = server.expect(requestTo(model.baseUrl() + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value(model.apiModelName()));
        switch (model) {
            case QWEN_PLUS, QWEN_MAX -> expectation
                    .andExpect(jsonPath("$.enable_thinking").value(false))
                    .andExpect(jsonPath("$.thinking").doesNotExist());
            case DEEPSEEK_FLASH, DEEPSEEK_PRO -> expectation
                    .andExpect(jsonPath("$.thinking.type").value("disabled"))
                    .andExpect(jsonPath("$.enable_thinking").doesNotExist());
        }
        if (probe) {
            expectation.andExpect(jsonPath("$.max_tokens").value(1))
                    .andExpect(jsonPath("$.temperature").value(0.0));
        } else {
            expectation.andExpect(jsonPath("$.max_tokens").doesNotExist());
        }
        expectation.andRespond(withSuccess("""
                {"id":"test","object":"chat.completion","created":1,
                 "choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"stop"}]}
                """, MediaType.APPLICATION_JSON));

        assertEquals("pong", client.call(new Prompt("ping")).getResult().getOutput().getText());
        server.verify();
    }
}
