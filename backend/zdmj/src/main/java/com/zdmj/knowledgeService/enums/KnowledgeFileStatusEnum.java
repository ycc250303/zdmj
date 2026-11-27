package com.zdmj.knowledgeService.enums;

/**
 * 知识文档原文件状态，对应 {@code knowledge_documents.file_status}，独立于向量化状态。
 */
public enum KnowledgeFileStatusEnum {
    /** 原文件正在写入，对应码值 0。 */
    WRITING(0),
    /** 原文件可用，对应码值 1。 */
    READY(1),
    /** 原文件写入失败，对应码值 2。 */
    FAILED(2),
    /** 原文件正在删除，禁止新的下载和向量提交，对应码值 3。 */
    DELETING(3);

    /** 原文件状态码，写入 knowledge_documents.file_status，取值为 0～3。 */
    private final int code;

    KnowledgeFileStatusEnum(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
