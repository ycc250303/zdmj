package com.zdmj.knowledgeService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 检索配置，在属性绑定时验证阈值、数量及资料预算。 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "spring.ai.rag")
public class RagConfig {
    /** 查询改写配置。 */
    @Valid
    private final Rewrite rewrite = new Rewrite();
    /** 排序及上下文配置。 */
    @Valid
    private final Search search = new Search();

    @Data
    public static class Rewrite {
        /** 是否在检索前调用模型改写问题。 */
        private boolean enabled = true;
    }

    @Data
    public static class Search {
        /** 短句长度上限，按 UTF-16 代码单元计数，必须为正数。 */
        @Positive
        private int shortQueryLength = 4;
        /** 中句长度上限，必须大于短句上限。 */
        @Positive
        private int mediumQueryLength = 16;
        /** 短句候选数量，必须为正数。 */
        @Positive
        private int topkShort = 20;
        /** 中句候选数量，必须为正数。 */
        @Positive
        private int topkMedium = 12;
        /** 长句候选数量，必须为正数。 */
        @Positive
        private int topkLong = 8;
        /** 短句最低得分，必须为 [0,1] 内的有限数值。 */
        private double minScoreShort = 0.18;
        /** 其他问题最低得分，必须为 [0,1] 内的有限数值。 */
        private double minScoreDefault = 0.28;
        /** 最终资料上下文的 UTF-16 长度上限，包含正文、分隔符和截断标记，必须为正数。 */
        @Positive
        private int contextBudget = 12000;

        @AssertTrue(message = "mediumQueryLength 必须大于 shortQueryLength")
        public boolean isQueryLengthOrderValid() {
            return mediumQueryLength > shortQueryLength;
        }

        @AssertTrue(message = "minScoreShort 和 minScoreDefault 必须为 [0,1] 内的有限数值")
        public boolean isScoreRangeValid() {
            return Double.isFinite(minScoreShort) && minScoreShort >= 0 && minScoreShort <= 1
                    && Double.isFinite(minScoreDefault) && minScoreDefault >= 0 && minScoreDefault <= 1;
        }
    }
}
