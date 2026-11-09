package com.zdmj.aiService.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证正式配置及示例只自动装配平台嵌入能力，构建过程不发送供应商请求。 */
class PlatformModelConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-example.yml"})
    void platformConfiguration_shouldOnlyCreateEmbeddingModel(String resource) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
                        OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class))
                .withPropertyValues("spring.config.location=classpath:/" + resource,
                        "spring.config.import=classpath:/empty.env[.properties]",
                        "SPRING_AI_OPENAI_API_KEY=test-platform-key",
                        "SPRING_AI_OPENAI_BASE_URL=http://127.0.0.1:9")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(ChatModel.class)
                            .hasSingleBean(EmbeddingModel.class);
                    var options = context.getBean(OpenAiEmbeddingProperties.class).getOptions();
                    assertThat(options.getModel()).isEqualTo("text-embedding-v4");
                    assertThat(options.getDimensions()).isEqualTo(1024);
                });
    }
}
