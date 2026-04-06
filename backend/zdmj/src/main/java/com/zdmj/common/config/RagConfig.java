package com.zdmj.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagConfig {
    /**
     * 是否启用RAG增强
     */
    private boolean enabled = true;
    
    /**
     * 是否启用查询改写
     */
    private boolean rewriteEnabled = true;

    /**
     * 上下文字符预算
     */
    private int contextCharBudget = 6000;

    /**
     * 检索结果数量
     */
    private int topK = 10;
}
