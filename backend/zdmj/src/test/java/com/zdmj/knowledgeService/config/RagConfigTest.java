package com.zdmj.knowledgeService.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RagConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Properties.class);
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RagConfig.class)
    static class Properties { }

    @Test
    void 默认配置和合法边界允许启动() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RagConfig.class).getSearch().getContextBudget()).isEqualTo(12000);
        });
        runner.withPropertyValues("spring.ai.rag.search.context-budget=1",
                "spring.ai.rag.search.min-score-short=0", "spring.ai.rag.search.min-score-default=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RagConfig.Search search = context.getBean(RagConfig.class).getSearch();
                    assertThat(search.getContextBudget()).isEqualTo(1);
                    assertThat(search.getMinScoreShort()).isZero();
                    assertThat(search.getMinScoreDefault()).isEqualTo(1);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-query-length=0", "short-query-length=-1", "medium-query-length=4",
            "medium-query-length=3", "topk-short=0", "topk-medium=-1", "topk-long=0",
            "context-budget=0", "context-budget=-1", "min-score-short=-0.01", "min-score-default=1.01",
            "min-score-short=NaN", "min-score-default=NaN", "min-score-short=Infinity", "min-score-default=-Infinity"})
    void 非法配置阻止启动(String property) {
        runner.withPropertyValues("spring.ai.rag.search." + property)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(org.springframework.boot.context.properties.bind.validation.BindValidationException.class));
    }
}
