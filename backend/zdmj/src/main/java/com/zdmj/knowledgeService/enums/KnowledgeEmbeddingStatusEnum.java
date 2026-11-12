package com.zdmj.knowledgeService.enums;

import java.util.Arrays;

/**
 * 知识文档的向量化状态。码值写入 {@code knowledge_documents.embedding_status}。
 */
public enum KnowledgeEmbeddingStatusEnum {
    /** 等待向量化。 */
    PENDING(1, "PENDING"),
    /** 正在向量化。 */
    RUNNING(2, "RUNNING"),
    /** 向量化成功。 */
    SUCCESS(3, "SUCCESS"),
    /** 向量化失败。 */
    FAILED(4, "FAILED");

    private final int code;
    private final String name;

    KnowledgeEmbeddingStatusEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * @return 持久化码值
     */
    public int getCode() {
        return code;
    }

    /**
     * @return 对外展示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 按持久化码值解析状态。
     *
     * @param code 文档向量化状态码，空值或未知码返回 null
     * @return 对应状态
     */
    public static KnowledgeEmbeddingStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
