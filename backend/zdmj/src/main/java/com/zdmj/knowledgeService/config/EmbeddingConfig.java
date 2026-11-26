package com.zdmj.knowledgeService.config;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入配置。
 */
@Configuration
@EnableConfigurationProperties(KnowledgeDocumentConfig.class)
public class EmbeddingConfig {

    /**
     * 创建知识文档分块器。
     *
     * <p>目标片段大小为 800 token，标点切分位置超过 350 个字符时允许按该位置截取。
     * 保留非空短片段，持续切分全部文本；输入总量由知识文档提取上限约束。</p>
     *
     * @return 知识文档分块器
     */
    @Bean
    public TextSplitter textSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(0)
                .withMaxNumChunks(Integer.MAX_VALUE)
                .withKeepSeparator(true)
                .build();
    }
}
