package com.zdmj.common.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.GetObjectMetadataRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 对象存储与用户域文件上传（腾讯云 COS）。
 */
@Slf4j
@Service
public class FileUploadService {

    @Value("${cos.secret-id}")
    private String secretId;

    @Value("${cos.secret-key}")
    private String secretKey;

    @Value("${cos.region}")
    private String region;

    @Value("${cos.bucket-name}")
    private String bucketName;

    private COSClient cosClient;

    @FunctionalInterface
    private interface CosCall<T> {
        T run() throws CosServiceException, CosClientException;
    }

    @FunctionalInterface
    private interface CosVoidCall {
        void run() throws CosServiceException, CosClientException;
    }

    @PostConstruct
    public void init() {
        try {
            if (isBlank(secretId)) {
                log.warn("COS SecretId未配置或使用默认值，请设置环境变量 COS_SECRET_ID");
            }
            if (isBlank(secretKey)) {
                log.warn("COS SecretKey未配置或使用默认值，请设置环境变量 COS_SECRET_KEY");
            }
            if (isBlank(bucketName)) {
                throw new RuntimeException("COS存储桶名称未配置，请设置环境变量 COS_BUCKET_NAME");
            }

            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            cosClient = new COSClient(cred, clientConfig);
            log.info("腾讯云COS客户端初始化成功，地域：{}，存储桶：{}", region, bucketName);
        } catch (Exception e) {
            log.error("腾讯云COS客户端初始化失败", e);
            throw new RuntimeException("COS客户端初始化失败：" + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("腾讯云COS客户端已关闭");
        }
    }

    public FileUploadResponse uploadFile(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String key = buildUserScopedKey(prefix, file.getOriginalFilename());
        String uploadedKey = putObject(file, key);
        return FileUploadResponse.builder()
                .key(uploadedKey)
                .url(getFileUrl(uploadedKey))
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    /**
     * 删除当前用户在指定业务域下上传的文件。
     */
    public void deleteOwnedByUrl(String fileUrl, String bizArea) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        Long userId = UserHolder.requireUserId();
        String key = requireOwnedKey(extractKeyFromUrl(fileUrl), userId);
        String expectedPrefix = UserObjectKeys.ownedPrefix(userId) + sanitizeBizArea(bizArea) + "/";
        if (!key.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION.getCode(), "无权删除该文件");
        }
        deleteObject(key);
        log.info("用户域文件已清理，key={}", key);
    }

    public void deleteByKey(String key) {
        validateUserKey(key);
        deleteObject(key);
        log.info("文件删除成功，key={}", key);
    }

    public List<FileUploadListItemResponse> listUploadedFiles(String prefix) {
        Long userId = UserHolder.requireUserId();
        String queryPrefix = (prefix == null || prefix.isBlank())
                ? String.format("user-%d/", userId)
                : String.format("user-%d/%s/", userId, sanitizeBizArea(prefix));
        List<String> keys = listKeysByPrefix(queryPrefix, userId);
        List<FileUploadListItemResponse> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            result.add(FileUploadListItemResponse.builder()
                    .key(key)
                    .url(getFileUrl(key))
                    .fileName(extractFileName(key))
                    .bizArea(extractBizArea(key, userId))
                    .build());
        }
        return result;
    }

    public boolean exists(String key) {
        String ownedKey = requireOwnedKey(key, UserHolder.requireUserId());
        try {
            cosClient.getObjectMetadata(new GetObjectMetadataRequest(bucketName, ownedKey));
            return true;
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw wrapCosServiceException("检查文件是否存在", e);
        } catch (CosClientException e) {
            throw wrapCosClientException("检查文件是否存在", e);
        }
    }

    public boolean isManagedCosUrl(String sourceUri) {
        if (isBlank(sourceUri) || isBlank(bucketName) || isBlank(region)) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUri.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
                return false;
            }
            String host = uri.getHost();
            if (isBlank(host)) {
                return false;
            }
            String expectedHost = bucketName + ".cos." + region + ".myqcloud.com";
            return expectedHost.equalsIgnoreCase(host);
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * 从 COS URL 读取当前用户拥有的对象流（SDK 鉴权，适用于私有桶）。调用方须关闭返回的 InputStream。
     */
    public InputStream openInputStreamFromUrl(String sourceUri) {
        return openInputStreamFromUrl(sourceUri, UserHolder.requireUserId());
    }

    /**
     * 从 COS URL 读取指定用户拥有的对象流。异步任务须传入文档所属 {@code ownerUserId}，勿依赖 ThreadLocal。
     */
    public InputStream openInputStreamFromUrl(String sourceUri, Long ownerUserId) {
        ensureInitialized();
        if (ownerUserId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (!isManagedCosUrl(sourceUri)) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR.getCode(), "仅支持本系统已上传的文件");
        }
        String key = requireOwnedKey(extractKeyFromUrl(sourceUri), ownerUserId);
        return execute("读取文件", () -> {
            COSObject cosObject = cosClient.getObject(new GetObjectRequest(bucketName, key));
            return cosObject.getObjectContent();
        });
    }

    public String extractKeyFromUrl(String sourceUri) {
        if (isBlank(sourceUri)) {
            return "";
        }
        String rawPath = sourceUri.trim();
        try {
            URI uri = URI.create(sourceUri.trim());
            if (!isBlank(uri.getPath())) {
                rawPath = uri.getPath();
            }
        } catch (Exception ignore) {
            // 非标准 URI 时按原字符串兜底处理
        }
        try {
            rawPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            // 保持未解码路径，后续 normalize 会拒绝非法段
        }
        String normalized = UserObjectKeys.normalize(rawPath);
        return normalized == null ? "" : normalized;
    }

    private String putObject(MultipartFile file, String key) {
        try {
            String finalKey = isBlank(key) ? generateKey(null, file.getOriginalFilename()) : key;
            try (InputStream inputStream = file.getInputStream()) {
                return execute("文件上传", () -> {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(file.getSize());
                    if (!isBlank(file.getContentType())) {
                        metadata.setContentType(file.getContentType());
                    }
                    PutObjectResult result = cosClient.putObject(new PutObjectRequest(
                            bucketName, finalKey, inputStream, metadata));
                    log.info("文件上传成功，key: {}, ETag: {}", finalKey, result.getETag());
                    return finalKey;
                });
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            log.error("文件上传异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("文件上传异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    private void deleteObject(String key) {
        executeVoid("文件删除", () -> {
            cosClient.deleteObject(bucketName, key);
            log.info("文件删除成功，key: {}", key);
        });
    }

    private String getFileUrl(String key) {
        ensureInitialized();
        return String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, region, key);
    }

    private List<String> listKeysByPrefix(String prefix, long userId) {
        ensureInitialized();
        return execute("查询文件列表", () -> {
            String normalizedPrefix = prefix == null ? "" : prefix.trim();
            List<String> keys = new ArrayList<>();
            String marker = null;
            do {
                ListObjectsRequest request = new ListObjectsRequest();
                request.setBucketName(bucketName);
                request.setPrefix(normalizedPrefix);
                request.setMarker(marker);
                request.setMaxKeys(1000);

                ObjectListing listing = cosClient.listObjects(request);
                for (COSObjectSummary summary : listing.getObjectSummaries()) {
                    String objectKey = summary.getKey();
                    if (objectKey != null && !objectKey.endsWith("/") && UserObjectKeys.isOwnedBy(objectKey, userId)) {
                        keys.add(objectKey);
                    }
                }
                marker = listing.isTruncated() ? listing.getNextMarker() : null;
            } while (marker != null);
            return keys;
        });
    }

    private String generateKey(String prefix, String originalFilename) {
        String extension = "";
        String fileNameWithoutExt = originalFilename;

        if (!isBlank(originalFilename)) {
            int dot = originalFilename.lastIndexOf(".");
            if (dot >= 0) {
                extension = originalFilename.substring(dot).toLowerCase();
                fileNameWithoutExt = originalFilename.substring(0, dot);
            }
        }

        if (!isBlank(fileNameWithoutExt)) {
            fileNameWithoutExt = fileNameWithoutExt.replaceAll("[^\\w\\u4e00-\\u9fa5-]", "_");
        }
        if (isBlank(fileNameWithoutExt)) {
            fileNameWithoutExt = "file";
        }

        String fileName = fileNameWithoutExt + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
        if (isBlank(prefix)) {
            return fileName;
        }
        return trimTrailingSlash(prefix) + "/" + fileName;
    }

    private String sanitizeBizArea(String prefix) {
        String bizArea = (prefix == null || prefix.isBlank()) ? "files" : prefix.trim();
        return bizArea.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String buildUserScopedKey(String prefix, String originalFilename) {
        Long userId = UserHolder.requireUserId();
        String bizArea = sanitizeBizArea(prefix);
        String finalPrefix = String.format("user-%d/%s", userId, bizArea);
        return generateKey(finalPrefix, originalFilename);
    }

    private void validateUserKey(String key) {
        requireOwnedKey(key, UserHolder.requireUserId());
    }

    private static String requireOwnedKey(String key, long userId) {
        String normalized = UserObjectKeys.normalize(key);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "无效的文件路径");
        }
        if (!UserObjectKeys.isOwnedBy(normalized, userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return normalized;
    }

    private String extractFileName(String key) {
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    private String extractBizArea(String key, Long userId) {
        String prefix = "user-" + userId + "/";
        if (!key.startsWith(prefix)) {
            return "";
        }
        String remain = key.substring(prefix.length());
        int slash = remain.indexOf('/');
        return slash > 0 ? remain.substring(0, slash) : "";
    }

    private <T> T execute(String action, CosCall<T> call) {
        try {
            return call.run();
        } catch (CosServiceException e) {
            throw wrapCosServiceException(action, e);
        } catch (CosClientException e) {
            throw wrapCosClientException(action, e);
        }
    }

    private void executeVoid(String action, CosVoidCall call) {
        execute(action, () -> {
            call.run();
            return null;
        });
    }

    private RuntimeException wrapCosServiceException(String action, CosServiceException e) {
        log.error("COS服务异常，action={}，错误码：{}，错误消息：{}", action, e.getErrorCode(), e.getErrorMessage(), e);
        return new RuntimeException(action + "失败：" + e.getErrorMessage(), e);
    }

    private RuntimeException wrapCosClientException(String action, CosClientException e) {
        log.error("COS客户端异常，action={}", action, e);
        return new RuntimeException(action + "失败：" + e.getMessage(), e);
    }

    private void ensureInitialized() {
        if (isBlank(region) || isBlank(bucketName) || cosClient == null) {
            throw new IllegalStateException("COS 客户端未初始化");
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
