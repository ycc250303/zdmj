package com.zdmj.testsupport;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.multipart.MultipartFile;

import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadListItemResponse;
import com.zdmj.common.storage.FileUploadResponse;
import com.zdmj.common.storage.FileUploadService;
import com.zdmj.common.storage.UserObjectKeys;

/**
 * 内存对象存储。键使用 {@code user-{id}/{bizArea}/{file}}，删除和读取校验归属。
 */
public class TestObjectStorage extends FileUploadService {

    public static final String URL_PREFIX = "https://test-object-storage.local/";

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, byte[]> objects = new ConcurrentHashMap<>();

    public List<String> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
        objects.clear();
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        long userId = UserHolder.requireUserId();
        String bizArea = sanitizeBizArea(prefix);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String key = UserObjectKeys.ownedPrefix(userId) + bizArea + "/" + fileName;
        try {
            objects.put(key, file.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException("读取上传内容失败", e);
        }
        calls.add("upload:" + key);
        return FileUploadResponse.builder()
                .key(key)
                .url(URL_PREFIX + key)
                .fileName(fileName)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    @Override
    public void deleteOwnedByUrl(String fileUrl, String bizArea) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        deleteByKey(extractKeyFromUrl(fileUrl));
        calls.add("deleteOwned:" + fileUrl);
    }

    @Override
    public void deleteByKey(String key) {
        String owned = requireOwned(key, UserHolder.requireUserId());
        objects.remove(owned);
        calls.add("delete:" + owned);
    }

    @Override
    public List<FileUploadListItemResponse> listUploadedFiles(String prefix) {
        long userId = UserHolder.requireUserId();
        String bizArea = prefix == null || prefix.isBlank() ? null : sanitizeBizArea(prefix);
        String queryPrefix = bizArea == null
                ? UserObjectKeys.ownedPrefix(userId)
                : UserObjectKeys.ownedPrefix(userId) + bizArea + "/";
        calls.add("list:" + queryPrefix);
        List<FileUploadListItemResponse> result = new ArrayList<>();
        for (String key : objects.keySet()) {
            if (!key.startsWith(queryPrefix)) {
                continue;
            }
            result.add(FileUploadListItemResponse.builder()
                    .key(key)
                    .url(URL_PREFIX + key)
                    .fileName(fileNameOf(key))
                    .bizArea(bizArea == null ? "" : bizArea)
                    .build());
        }
        return result;
    }

    @Override
    public boolean exists(String key) {
        String normalized = UserObjectKeys.normalize(key);
        calls.add("exists:" + normalized);
        return normalized != null && objects.containsKey(normalized);
    }

    @Override
    public boolean isManagedCosUrl(String sourceUri) {
        if (sourceUri == null || !sourceUri.startsWith(URL_PREFIX)) {
            return false;
        }
        return UserObjectKeys.normalize(extractKeyFromUrl(sourceUri)) != null;
    }

    @Override
    public InputStream openInputStreamFromUrl(String sourceUri, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (!isManagedCosUrl(sourceUri)) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR, "仅支持本系统已上传的文件");
        }
        String owned = requireOwned(extractKeyFromUrl(sourceUri), ownerUserId);
        byte[] bytes = objects.get(owned);
        if (bytes == null) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS);
        }
        calls.add("open:" + owned);
        return new ByteArrayInputStream(bytes);
    }

    private static String requireOwned(String key, long userId) {
        String normalized = UserObjectKeys.normalize(key);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的文件路径");
        }
        if (!UserObjectKeys.isOwnedBy(normalized, userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return normalized;
    }

    private static String sanitizeBizArea(String prefix) {
        String bizArea = (prefix == null || prefix.isBlank()) ? "files" : prefix.trim();
        return bizArea.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String sanitizeFileName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || name.contains("..")) {
            return "file";
        }
        return name;
    }

    private static String fileNameOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
