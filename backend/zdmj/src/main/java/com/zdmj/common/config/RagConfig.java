package com.zdmj.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.rag")
public class RagConfig {

    /** 是否启用RAG */
    private boolean enabled = true;

    private final Rewrite rewrite = new Rewrite();
    private final Search search = new Search();

    @Data
    public static class Rewrite {
        /** 是否在检索前调用 LLM 做查询改写 */
        private boolean enabled = true;
    }

    @Data
    public static class Search {
        /** 短句长度阈值（字符数），用于选择 topK / minScore */
        private int shortQueryLength = 4;
        private int mediumQueryLength = 16;
        private int topkShort = 20;
        private int topkMedium = 12;
        private int topkLong = 8;
        private double minScoreShort = 0.18;
        private double minScoreDefault = 0.28;
        /** RAG 拼入模型的资料总字符预算；过小会导致仅第一条命中占满、后续块几乎进不了上下文 */
        private int contextBudget = 12000;
    }

    public Rewrite getRewrite() {
        return rewrite;
    }

    public Search getSearch() {
        return search;
    }
}
