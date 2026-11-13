package com.zdmj.common.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.GetObjectMetadataRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

/** 用户文件存储。对象键统一为 user-{id}/{category}/{file}，所有操作验证用户归属。 */
@Slf4j
@Service
@Profile("!test")
public class FileUploadService {
    private final COSClient cosClient;
    private final String bucketName;
    private final String baseUrl;
    private final String host;

    public FileUploadService(COSClient cosClient, @Value("${cos.bucket-name}") String bucketName,
            @Value("${cos.region}") String region) {
        this.cosClient = Objects.requireNonNull(cosClient);
        this.bucketName = bucketName;
        this.host = bucketName + ".cos." + region + ".myqcloud.com";
        this.baseUrl = "https://" + host + "/";
    }

    /**
     * 上传文件
     * @param actor 当前操作者
     * @param file 非空上传文件
     * @param category 文件分类
     * @return 对象键、地址及原文件信息
     * @throws BusinessException 文件为空、读取失败或存储不可用
     */
    public FileUploadResponse uploadFile(CurrentActor actor, MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String key = UserObjectKeys.ownedPrefix(actor.userId()) + category.path() + "/"
                + generateFileName(file.getOriginalFilename());
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (file.getContentType() != null) {
            metadata.setContentType(file.getContentType());
        }
        try (InputStream input = file.getInputStream()) {
            execute(() -> cosClient.putObject(new PutObjectRequest(bucketName, key, input, metadata)));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, exception);
        }
        log.info("文件上传成功，key={}", key);
        return FileUploadResponse.builder().key(key).url(fileUrl(key)).fileName(file.getOriginalFilename())
                .fileSize(file.getSize()).contentType(file.getContentType()).build();
    }

    /**
     * 删除指定 URL 的文件。
     *
     * @param actor 当前操作者
     * @param url 文件 URL
     * @param category 文件分类
     */
    public void deleteOwnedByUrl(CurrentActor actor, String url, FileCategory category) {
        if (url == null || url.isBlank()) {
            return;
        }
        String key = requireOwnedKeyFromUrl(actor, url);
        if (!key.startsWith(UserObjectKeys.ownedPrefix(actor.userId()) + category.path() + "/")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权删除该文件");
        }
        deleteByKey(actor, key);
    }

    /** 按对象键删除当前用户文件
     * @param actor 当前操作者
     * @param key 对象键    
     */
    public void deleteByKey(CurrentActor actor, String key) {
        String owned = requireOwnedKey(actor, key);
        execute(() -> {
            cosClient.deleteObject(bucketName, owned);
            return null;
        });
        log.info("文件删除成功，key={}", owned);
    }

    /** 查询分类目录；category 为空时查询当前用户全部文件，并读取所有 COS 分页。 */
    public List<FileUploadListItemResponse> listUploadedFiles(CurrentActor actor, FileCategory category) {
        String prefix = UserObjectKeys.ownedPrefix(actor.userId()) + (category == null ? "" : category.path() + "/");
        return execute(() -> {
            List<FileUploadListItemResponse> files = new ArrayList<>();
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucketName);
            request.setPrefix(prefix);
            request.setMaxKeys(1000);
            ObjectListing listing;
            do {
                listing = cosClient.listObjects(request);
                for (var summary : listing.getObjectSummaries()) {
                    String key = summary.getKey();
                    if (key == null || key.endsWith("/") || !key.startsWith(prefix)
                            || !UserObjectKeys.isOwnedBy(key, actor.userId())) {
                        continue;
                    }
                    String relative = key.substring(UserObjectKeys.ownedPrefix(actor.userId()).length());
                    int slash = relative.indexOf('/');
                    files.add(FileUploadListItemResponse.builder().key(key).url(fileUrl(key))
                            .fileName(key.substring(key.lastIndexOf('/') + 1))
                            .bizArea(slash > 0 ? relative.substring(0, slash) : "").build());
                }
                if (listing.isTruncated() && (listing.getNextMarker() == null
                        || listing.getNextMarker().equals(request.getMarker()))) {
                    throw new IllegalStateException("COS 列表缺少有效的下一页标识");
                }
                request.setMarker(listing.getNextMarker());
            } while (listing.isTruncated());
            return files;
        });
    }

    /** 地址和归属校验通过后检查存在性；仅对象不存在的 404 返回 false。 */
    public boolean existsByUrl(CurrentActor actor, String url) {
        String key = requireOwnedKeyFromUrl(actor, url);
        return execute(() -> {
            try {
                cosClient.getObjectMetadata(new GetObjectMetadataRequest(bucketName, key));
                return true;
            } catch (CosServiceException exception) {
                if (exception.getStatusCode() == 404) {
                    return false;
                }
                throw exception;
            }
        });
    }

    /**
     * 通过 SDK 读取已验证的用户对象；调用方负责关闭返回流。
     * @param actor 当前操作者
     * @param url 本系统上传文件的 HTTPS 地址
     * @return 对象内容输入流
     * @throws BusinessException 地址非法、无权访问或存储不可用
     */
    public InputStream openInputStreamFromUrl(CurrentActor actor, String url) {
        String key = requireOwnedKeyFromUrl(actor, url);
        return execute(() -> cosClient.getObject(new GetObjectRequest(bucketName, key)).getObjectContent());
    }

    private String requireOwnedKeyFromUrl(CurrentActor actor, String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR, exception);
        }
        String path = uri.getPath();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !host.equalsIgnoreCase(uri.getHost())
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\")
                || uri.getRawPath().matches("(?i).*%(2f|5c).*")) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR, "仅支持本系统已上传的文件");
        }
        return requireOwnedKey(actor, path.substring(1));
    }

    private static String requireOwnedKey(CurrentActor actor, String key) {
        String normalized = UserObjectKeys.normalize(key);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的文件路径");
        }
        if (!normalized.startsWith(UserObjectKeys.ownedPrefix(actor.userId()))) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return normalized;
    }

    private String fileUrl(String key) {
        return baseUrl + UriUtils.encodePath(key, StandardCharsets.UTF_8);
    }

    private static String generateFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^.a-z0-9]", "_");
        String stem = (dot < 0 ? name : name.substring(0, dot)).replaceAll("[^\\w\\u4e00-\\u9fa5-]", "_");
        return (stem.isBlank() ? "file" : stem) + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (CosClientException exception) {
            throw new BusinessException(ErrorCode.OBJECT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** COS 客户端由容器创建和关闭；测试环境使用独立替身。 */
    @Configuration(proxyBeanMethods = false)
    @Profile("!test")
    public static class CosStorageConfiguration {
        @Bean(destroyMethod = "shutdown")
        public COSClient cosClient(Environment environment) {
            if (!"cos".equals(environment.getProperty("zdmj.storage.mode", "cos"))) {
                throw new IllegalStateException("对象存储模式必须为 cos");
            }
            String secretId = required(environment, "cos.secret-id");
            String secretKey = required(environment, "cos.secret-key");
            String region = required(environment, "cos.region");
            required(environment, "cos.bucket-name");
            return new COSClient(new BasicCOSCredentials(secretId, secretKey), new ClientConfig(new Region(region)));
        }

        private static String required(Environment environment, String name) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("缺少对象存储配置：" + name);
            }
            return value;
        }
    }
}
