package com.zdmj.knowledgeService.dto;

/**
 * 已校验归属的下载资源，输入流由 HTTP 响应转换器关闭。
 * @param input 原文件输入流，不得为空
 * @param fileName 附件名称，不得为空
 * @param fileSize 附件字节数，必须为正数
 * @param contentType 附件媒体类型，不得为空
 */
public record KnowledgeDocumentDownload(java.io.InputStream input, String fileName, long fileSize, String contentType) {
    public KnowledgeDocumentDownload {
        java.util.Objects.requireNonNull(input);
        java.util.Objects.requireNonNull(fileName);
        java.util.Objects.requireNonNull(contentType);
        if (fileSize <= 0) { throw new IllegalArgumentException("文件长度无效"); }
    }
}
