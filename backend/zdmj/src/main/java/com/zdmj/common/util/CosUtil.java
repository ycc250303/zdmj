package com.zdmj.common.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 腾讯云 COS 工具类（上传、删除、列举、URL 解析等）。
 * 参考文档：https://cloud.tencent.com/document/product/436/10199
 */
@Slf4j
@Component
public class CosUtil {

    @Value("${cos.secret-id}")
    private String secretId;

    @Value("${cos.secret-key}")
    private String secretKey;

    @Value("${cos.region}")
    private String region;

    @Value("${cos.bucket-name}")
    private String bucketName;

    private static COSClient cosClient;
    private static String staticBucketName;
    private static String staticRegion;

    @FunctionalInterface
    private interface CosCall<T> {
        T run() throws CosServiceException, CosClientException;
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
            staticBucketName = bucketName;
            staticRegion = region;

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

    /**
     * 生成 COS 对象键
     * @param prefix 前缀
     * @param originalFilename 原始文件名
     * @return COS 对象键
     */
    public static String generateKey(String prefix, String originalFilename) {
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

    public static String generateKey(String originalFilename) {
        return generateKey(null, originalFilename);
    }

    /**
     * 上传文件
     * @param file 文件
     * @param key COS对象键
     * @return COS对象键
     */
    public static String uploadFile(MultipartFile file, String key) {
        try {
            String finalKey = isBlank(key) ? generateKey(file.getOriginalFilename()) : key;
            try (InputStream inputStream = file.getInputStream()) {
                return execute("文件上传", () -> {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(file.getSize());
                    if (!isBlank(file.getContentType())) {
                        metadata.setContentType(file.getContentType());
                    }
                    PutObjectResult result = cosClient.putObject(new PutObjectRequest(
                            staticBucketName, finalKey, inputStream, metadata));
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

    public static void deleteFile(String key) {
        executeVoid("文件删除", () -> {
            cosClient.deleteObject(staticBucketName, key);
            log.info("文件删除成功，key: {}", key);
        });
    }

    public static boolean fileExists(String key) {
        try {
            cosClient.getObjectMetadata(new GetObjectMetadataRequest(staticBucketName, key));
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

    public static String getFileUrl(String key) {
        ensureInitialized();
        return String.format("https://%s.cos.%s.myqcloud.com/%s", staticBucketName, staticRegion, key);
    }

    /**
     * 判断 URL 是否指向当前应用配置的 COS 存储桶。
     */
    public static boolean isManagedCosUrl(String sourceUri) {
        if (isBlank(sourceUri) || staticBucketName == null || staticRegion == null) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUri.trim());
            String host = uri.getHost();
            if (isBlank(host)) {
                return false;
            }
            String expectedHost = staticBucketName + ".cos." + staticRegion + ".myqcloud.com";
            return expectedHost.equalsIgnoreCase(host);
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * 从 COS URL 读取对象流（使用 SDK 鉴权，适用于私有桶）。
     * 调用方须关闭返回的 InputStream。
     */
    public static InputStream openInputStreamFromUrl(String sourceUri) {
        ensureInitialized();
        String key = extractKeyFromUrl(sourceUri);
        if (isBlank(key)) {
            throw new RuntimeException("无法从 URL 解析 COS 对象键");
        }
        return execute("读取文件", () -> {
            COSObject cosObject = cosClient.getObject(new GetObjectRequest(staticBucketName, key));
            return cosObject.getObjectContent();
        });
    }

    public static String extractKeyFromUrl(String sourceUri) {
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
        rawPath = rawPath.replace('\\', '/');
        while (rawPath.startsWith("/")) {
            rawPath = rawPath.substring(1);
        }
        if (isBlank(rawPath)) {
            return "";
        }
        try {
            return URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return rawPath;
        }
    }

    public static List<String> listKeysByPrefix(String prefix) {
        ensureInitialized();
        return execute("查询文件列表", () -> {
            String normalizedPrefix = prefix == null ? "" : prefix.trim();
            List<String> keys = new ArrayList<>();
            String marker = null;
            do {
                ListObjectsRequest request = new ListObjectsRequest();
                request.setBucketName(staticBucketName);
                request.setPrefix(normalizedPrefix);
                request.setMarker(marker);
                request.setMaxKeys(1000);

                ObjectListing listing = cosClient.listObjects(request);
                for (COSObjectSummary summary : listing.getObjectSummaries()) {
                    String objectKey = summary.getKey();
                    if (objectKey != null && !objectKey.endsWith("/")) {
                        keys.add(objectKey);
                    }
                }
                marker = listing.isTruncated() ? listing.getNextMarker() : null;
            } while (marker != null);
            return keys;
        });
    }

    private static <T> T execute(String action, CosCall<T> call) {
        try {
            return call.run();
        } catch (CosServiceException e) {
            throw wrapCosServiceException(action, e);
        } catch (CosClientException e) {
            throw wrapCosClientException(action, e);
        }
    }

    private static void executeVoid(String action, CosVoidCall call) {
        execute(action, () -> {
            call.run();
            return null;
        });
    }

    private static RuntimeException wrapCosServiceException(String action, CosServiceException e) {
        log.error("COS服务异常，action={}，错误码：{}，错误消息：{}", action, e.getErrorCode(), e.getErrorMessage(), e);
        return new RuntimeException(action + "失败：" + e.getErrorMessage(), e);
    }

    private static RuntimeException wrapCosClientException(String action, CosClientException e) {
        log.error("COS客户端异常，action={}", action, e);
        return new RuntimeException(action + "失败：" + e.getMessage(), e);
    }

    @FunctionalInterface
    private interface CosVoidCall {
        void run() throws CosServiceException, CosClientException;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static void ensureInitialized() {
        if (staticRegion == null || staticBucketName == null) {
            throw new IllegalStateException("COS工具类未初始化，请确保CosUtil已被Spring容器管理");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
