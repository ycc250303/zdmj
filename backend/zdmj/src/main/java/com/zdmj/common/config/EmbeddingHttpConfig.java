package com.zdmj.common.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** 平台嵌入使用有界连接与读取超时，用户对话继续使用独立客户端配置。 */
@Configuration
public class EmbeddingHttpConfig {
    /** Spring AI 1.1 的嵌入自动配置使用 Spring Boot 管理的 RestClient.Builder。 */
    @Bean
    RestClientCustomizer embeddingRestClientCustomizer() {
        return builder -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(120));
            builder.requestFactory(factory);
        };
    }
}
