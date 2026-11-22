package com.zdmj.knowledgeService.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmbeddingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmbeddingConfig.class);

    @Test
    void 未配置容量时使用一百万上限() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KnowledgeDocumentConfig.class).maxExtractedChars()).isEqualTo(1_000_000);
        });
    }

    @Test
    void 显式容量覆盖默认值() {
        contextRunner.withPropertyValues("knowledge.document.max-extracted-chars=200000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KnowledgeDocumentConfig.class).maxExtractedChars()).isEqualTo(200_000);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 非正数容量拒绝启动(int capacity) {
        contextRunner.withPropertyValues("knowledge.document.max-extracted-chars=" + capacity)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 长文本切分后保留中文和短尾部() {
        String text = "知识文档包含岗位要求和项目经验。\n".repeat(8_000) + "尾部";
        List<Document> chunks = new EmbeddingConfig().textSplitter().apply(List.of(new Document(text)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.getLast().getText()).endsWith("尾部");
        assertThat(String.join("", chunks.stream().map(Document::getText).toList()).replaceAll("\\s+", ""))
                .isEqualTo(text.replaceAll("\\s+", ""));
        assertThat(new EmbeddingConfig().textSplitter().apply(List.of(new Document("尾部"))))
                .singleElement().extracting(Document::getText).isEqualTo("尾部");
    }
}
