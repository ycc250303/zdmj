package com.zdmj.aiService.config;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import com.zdmj.common.config.EmbeddingHttpConfig;
import com.zdmj.aiService.service.impl.EmbeddingGatewayImpl;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmbeddingHttpConfigTest {
    @Test
    void 平台嵌入发送配置模型与维度且供应商错误只调用一次() throws Exception {
        var requests = new AtomicInteger(); var body = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int index = requests.incrementAndGet();
            String response = index == 1
                    ? "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1.0,0.0]}],\"model\":\"text-embedding-v4\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}"
                    : "{\"error\":{\"message\":\"test failure\",\"type\":\"server_error\"}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(index == 1 ? 200 : 503, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class,
                            SpringAiRetryAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class))
                    .withUserConfiguration(EmbeddingHttpConfig.class)
                    .withPropertyValues("spring.ai.openai.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                            "spring.ai.openai.api-key=test-only", "spring.ai.retry.max-attempts=1",
                            "spring.ai.openai.embedding.options.model=text-embedding-v4",
                            "spring.ai.openai.embedding.options.dimensions=1024")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var gateway = new EmbeddingGatewayImpl(context.getBean(EmbeddingModel.class));
                        assertThat(gateway.embedSystem(List.of("技术资料")).getFirst())
                                .containsExactly(1.0F, 0.0F);
                        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body.get());
                        assertThat(json.path("model").asText()).isEqualTo("text-embedding-v4");
                        assertThat(json.path("dimensions").asInt()).isEqualTo(1024);
                        assertThatThrownBy(() -> gateway.embedSystem(List.of("技术资料")))
                                .isInstanceOf(RuntimeException.class);
                        assertThat(requests).hasValue(2);
                    });
        } finally { server.stop(0); }
    }
}
