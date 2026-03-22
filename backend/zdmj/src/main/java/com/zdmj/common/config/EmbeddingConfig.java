package com.zdmj.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * Embedding模型配置类
 * 确保向量维度与数据库一致（1024维）
 * 
 * 功能：
 * 1. 在应用启动时验证EmbeddingModel生成的向量维度是否为1024维
 * 2. 如果维度不匹配，记录警告日志
 * 3. 如果使用text-embedding-3-small（1536维），需要在VectorRetrievalServiceImpl中处理维度转换
 * 
 * 注意：
 * - text-embedding-v4模型自动生成1024维向量，无需转换
 * - 如果使用text-embedding-3-small（1536维），需要在生成向量后进行截断处理
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    /**
     * 目标向量维度（与数据库pgvector表定义一致）
     */
    public static final int TARGET_DIMENSIONS = 1024;

    private final EmbeddingModel embeddingModel;

    public EmbeddingConfig(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 应用启动完成后验证EmbeddingModel的向量维度
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateEmbeddingDimensions() {
        try {
            log.info("验证EmbeddingModel向量维度，目标维度: {}", TARGET_DIMENSIONS);

            EmbeddingRequest testRequest = new EmbeddingRequest(List.of("test"), null);
            EmbeddingResponse testResponse = embeddingModel.call(testRequest);

            if (testResponse != null && testResponse.getResult() != null
                    && testResponse.getResult().getOutput() != null) {
                float[] testVector = testResponse.getResult().getOutput();
                int actualDimensions = testVector.length;

                log.info("EmbeddingModel实际维度: {}", actualDimensions);

                if (actualDimensions == TARGET_DIMENSIONS) {
                    log.info("✓ 向量维度匹配（{}维），无需转换", actualDimensions);
                } else if (actualDimensions > TARGET_DIMENSIONS) {
                    log.warn("⚠ 向量维度不匹配: 实际{}维，目标{}维。如果使用text-embedding-3-small，" +
                            "需要在VectorRetrievalServiceImpl中截断向量到{}维",
                            actualDimensions, TARGET_DIMENSIONS, TARGET_DIMENSIONS);
                } else {
                    log.error("✗ 向量维度不足: 实际{}维，目标{}维，无法使用",
                            actualDimensions, TARGET_DIMENSIONS);
                }
            } else {
                log.warn("无法获取EmbeddingModel测试结果");
            }
        } catch (Exception e) {
            log.warn("验证EmbeddingModel维度时出错: {}", e.getMessage());
        }
    }

    /**
     * 截断向量到目标维度（如果实际维度大于目标维度）
     * 供VectorRetrievalServiceImpl使用
     * 
     * @param vector 原始向量
     * @return 截断后的向量（1024维）
     */
    public static float[] truncateVector(float[] vector) {
        if (vector == null || vector.length <= TARGET_DIMENSIONS) {
            return vector;
        }

        float[] truncated = new float[TARGET_DIMENSIONS];
        System.arraycopy(vector, 0, truncated, 0, TARGET_DIMENSIONS);
        return truncated;
    }
}
