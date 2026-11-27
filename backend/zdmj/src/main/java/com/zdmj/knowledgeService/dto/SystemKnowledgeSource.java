package com.zdmj.knowledgeService.dto;

/**
 * 系统知识库已验证的正文快照。
 * @param path 来源仓库内docs相对路径
 * @param title 标题，最多500个UTF-16代码单元
 * @param hash 清理正文SHA-256
 * @param url 固定来源提交的原文地址
 * @param commit 上游40位Git提交编号
 * @param bytes 清理正文UTF-8字节，构造和读取时复制
 */
public record SystemKnowledgeSource(String path, String title, String hash, String url, String commit, byte[] bytes) {
    public SystemKnowledgeSource { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
