package com.zdmj.common.storage;

import java.util.Objects;

/**
 * 可信原文件描述，由存储服务生成并用于文件登记和写入校验。
 * @param key 用户目录内的相对文件标识，不得为空
 * @param fileName 原文件名称，不得为空
 * @param fileSize 文件字节数，必须为正数且不超过存储大小上限
 * @param contentType 服务器确定的媒体类型，不得为空
 */
public record StoredFile(String key, String fileName, long fileSize, String contentType) {
    /** 原文件字节数上限，与接收及写入校验共用。 */
    static final long MAX_SIZE = 20L * 1024 * 1024;

    public StoredFile {
        Objects.requireNonNull(key);
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(contentType);
        if (fileSize <= 0 || fileSize > MAX_SIZE) {
            throw new IllegalArgumentException("原文件长度无效");
        }
    }
}
