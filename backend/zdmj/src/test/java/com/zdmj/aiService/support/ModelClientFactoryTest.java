package com.zdmj.aiService.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.zdmj.aiService.enums.ModelCode;

import io.netty.buffer.Unpooled;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class ModelClientFactoryTest {

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousRequest_whenProviderFails_shouldStopWithoutRetry(boolean probe) {
        ModelClientFactory configuration = new ModelClientFactory();
        OpenAiChatModel client = (OpenAiChatModel) (probe
                ? configuration.buildProbe(ModelCode.DEEPSEEK_FLASH, "sk-test")
                : configuration.buildUserModel(ModelCode.DEEPSEEK_FLASH, "sk-test"));
        OpenAiApi api = (OpenAiApi) ReflectionTestUtils.getField(client, "openAiApi");
        RestClient.Builder builder = ((RestClient) ReflectionTestUtils.getField(api, "restClient")).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(api, "restClient", builder.build());
        server.expect(requestTo(ModelCode.DEEPSEEK_FLASH.baseUrl() + "/v1/chat/completions"))
                .andRespond(withServerError());
        // 后续请求一旦发生即违反期望；所有请求均由本地替身处理。
        assertThrows(TransientAiException.class,
                () -> client.call(new Prompt("ping")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stream_whenResponseStops_shouldTimeoutAndCloseConnection(boolean sendFirstChunk) throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean received = new AtomicBoolean();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1").port(0)
                .doOnConnection(connection -> connection.channel().closeFuture()
                        .addListener(future -> closed.countDown()))
                .handle((request, response) -> request.receive().then(response.header("Content-Type", "text/event-stream").send(
                            (sendFirstChunk ? Flux.just(chunk(), chunk())
                                    : Flux.<String>empty())
                                    .concatWith(Flux.never())
                                    .map(text -> Unpooled.copiedBuffer(
                                            text, StandardCharsets.UTF_8)), buffer -> true).then())).bindNow();
        try {
            var client = localClient(server.port(), 500);
            assertThatThrownBy(() -> client.stream(new Prompt("hi"))
                    .doOnNext(response -> received.set(true)).blockLast(Duration.ofSeconds(5)))
                    .hasRootCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);
            assertEquals(sendFirstChunk, received.get());
            assertTrue(closed.await(5, TimeUnit.SECONDS));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void stream_whenCancelled_shouldCloseProviderConnection() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1").port(0)
                .doOnConnection(connection -> connection.channel().closeFuture()
                        .addListener(future -> closed.countDown()))
                .handle((request, response) -> request.receive().then(response.header("Content-Type", "text/event-stream").send(
                            Flux.interval(Duration.ofMillis(50)).map(index -> chunk())
                                    .map(text -> Unpooled.copiedBuffer(
                                            text, StandardCharsets.UTF_8)), buffer -> true).then())).bindNow();
        Disposable subscription = null;
        try {
            subscription = localClient(server.port(), 30_000).stream(new Prompt("hi"))
                    .subscribe(response -> received.countDown());
            assertTrue(received.await(5, TimeUnit.SECONDS));
            subscription.dispose();
            assertTrue(closed.await(5, TimeUnit.SECONDS));
        } finally {
            if (subscription != null) {
                subscription.dispose();
            }
            server.disposeNow();
        }
    }

    private static OpenAiChatModel localClient(int port, int readTimeoutMs) {
        OpenAiApi api = ReflectionTestUtils.invokeMethod(new ModelClientFactory(), "buildOpenAiApi",
                "http://127.0.0.1:" + port, "sk-test", 1000, readTimeoutMs);
        return OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("test-model").build()).build();
    }

    private static String chunk() {
        return "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                + "\"content\":\"hello\"},\"finish_reason\":null}]}\n\n";
    }

    private static void verifyRequest(ModelCode model, boolean probe) {
        ModelClientFactory configuration = new ModelClientFactory();
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
