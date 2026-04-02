package com.zdmj.common.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 腾讯云COS工具类
 * 提供文件上传、删除等静态方法
 * 注意：下载功能由Python端完成，Java端仅负责上传
 * 
 * 参考文档：https://cloud.tencent.com/document/product/436/10199
 * 
 * 使用示例：
 * 
 * <pre>
 * // 上传文件
 * String key = CosUtil.uploadFile(file, "project/docs/");
 * 
 * // 删除文件
 * CosUtil.deleteFile(key);
 * 
 * // 生成预签名上传URL
 * String url = CosUtil.generatePresignedUploadUrl(key, 3600);
 * 
 * // 获取文件访问URL
 * String url = CosUtil.getFileUrl(key);
 * </pre>
 */
@Slf4j
@Component
public class CosUtil {
    private static final String DEFAULT_SECRET_ID = "your-secret-id";
    private static final String DEFAULT_SECRET_KEY = "your-secret-key";

    @Value("${cos.secret-id}")
    private String secretId;

    @Value("${cos.secret-key}")
    private String secretKey;

    @Value("${cos.region}")
    private String region;

    @Value("${cos.bucket-name}")
    private String bucketName;

    @Value("${cos.presigned-url-expiration:3600}")
    private Long presignedUrlExpiration;

    private static COSClient cosClient;
    private static String staticBucketName;
    private static String staticRegion;

    /**
     * 初始化COS客户端
     */
    @PostConstruct
    public void init() {
        try {
            // 验证配置
            if (isBlank(secretId) || DEFAULT_SECRET_ID.equals(secretId)) {
                log.warn("COS SecretId未配置或使用默认值，请设置环境变量 COS_SECRET_ID");
            }
            if (isBlank(secretKey) || DEFAULT_SECRET_KEY.equals(secretKey)) {
                log.warn("COS SecretKey未配置或使用默认值，请设置环境变量 COS_SECRET_KEY");
            }
            if (isBlank(bucketName)) {
                throw new RuntimeException("COS存储桶名称未配置，请设置环境变量 COS_BUCKET_NAME");
            }

            // 1. 初始化用户身份信息（secretId, secretKey）
            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);

            // 2. 设置bucket的地域
            Region regionObj = new Region(region);
            ClientConfig clientConfig = new ClientConfig(regionObj);

            // 3. 生成cos客户端
            cosClient = new COSClient(cred, clientConfig);

            // 保存静态变量供静态方法使用
            staticBucketName = bucketName;
            staticRegion = region;

            log.info("腾讯云COS客户端初始化成功，地域：{}，存储桶：{}", region, bucketName);
            log.info("提示：存储桶名称格式应为 bucketname-appid（如：mybucket-1234567890）");
        } catch (Exception e) {
            log.error("腾讯云COS客户端初始化失败", e);
            throw new RuntimeException("COS客户端初始化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 销毁COS客户端
     */
    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("腾讯云COS客户端已关闭");
        }
    }

    /**
     * 生成文件路径（对象键）
     * 格式：{prefix}/{文档名称}-{uuid}.{ext}
     * 
     * @param prefix           路径前缀，如 "project/docs"
     * @param originalFilename 原始文件名
     * @return 文件路径（对象键）
     */
    public static String generateKey(String prefix, String originalFilename) {
        String extension = "";
        String fileNameWithoutExt = originalFilename;

        // 拆分扩展名
        if (!isBlank(originalFilename)) {
            int dot = originalFilename.lastIndexOf(".");
            if (dot >= 0) {
                extension = originalFilename.substring(dot).toLowerCase();
                fileNameWithoutExt = originalFilename.substring(0, dot);
            }
        }

        // 清洗文件名（避免特殊字符导致的路径/URL问题）
        if (!isBlank(fileNameWithoutExt)) {
            fileNameWithoutExt = fileNameWithoutExt.replaceAll("[^\\w\\u4e00-\\u9fa5-]", "_");
        }
        if (isBlank(fileNameWithoutExt)) {
            fileNameWithoutExt = "file";
        }

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = fileNameWithoutExt + "-" + uuid + extension;

        if (isBlank(prefix)) {
            return fileName;
        }
        return trimTrailingSlash(prefix) + "/" + fileName;
    }

    /**
     * 生成文件路径（对象键），不带前缀
     * 
     * @param originalFilename 原始文件名
     * @return 文件路径（对象键）
     */
    public static String generateKey(String originalFilename) {
        return generateKey(null, originalFilename);
    }

    /**
     * 上传文件到COS
     */
    public static String uploadFile(MultipartFile file, String key) {
        try {
            String finalKey = isBlank(key) ? generateKey(file.getOriginalFilename()) : key;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (!isBlank(file.getContentType())) {
                metadata.setContentType(file.getContentType());
            }

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    staticBucketName,
                    finalKey,
                    file.getInputStream(),
                    metadata);
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
            log.info("文件上传成功，key: {}, ETag: {}", finalKey, putObjectResult.getETag());
            return finalKey;
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}，状态码：{}，请求ID：{}",
                    e.getErrorCode(), e.getErrorMessage(), e.getStatusCode(), e.getRequestId(), e);
            throw new RuntimeException("文件上传失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("文件上传异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     * 
     * @param key 文件路径（对象键）
     * @throws RuntimeException 删除失败时抛出
     */
    public static void deleteFile(String key) {
        try {
            cosClient.deleteObject(staticBucketName, key);
            log.info("文件删除成功，key: {}", key);
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("文件删除失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("文件删除失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检查文件是否存在
     * 
     * @param key 文件路径（对象键）
     * @return 是否存在
     * @throws RuntimeException 检查失败时抛出
     */
    public static boolean fileExists(String key) {
        try {
            GetObjectMetadataRequest getObjectMetadataRequest = new GetObjectMetadataRequest(staticBucketName, key);
            cosClient.getObjectMetadata(getObjectMetadataRequest);
            return true;
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            log.error("COS服务异常，错误码：{}，错误消息：{}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("检查文件是否存在失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("检查文件是否存在失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取对象输入流（调用方负责关闭）
     */
    public static COSObjectInputStream getObjectInputStream(String key) {
        try {
            COSObject cosObject = cosClient.getObject(staticBucketName, key);
            return cosObject.getObjectContent();
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("获取文件流失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("获取文件流失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取文件访问URL（永久URL）
     * 格式：https://{bucket}.cos.{region}.myqcloud.com/{key}
     * 
     * @param key        文件路径（对象键）
     * @param region     地域，如 "ap-beijing"、"ap-shanghai"
     * @param bucketName 存储桶名称
     * @return 文件访问URL
     */
    public static String getFileUrl(String key, String region, String bucketName) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, region, key);
    }

    /**
     * 获取文件访问URL（使用配置的region和bucket）
     * 
     * @param key 文件路径（对象键）
     * @return 文件访问URL
     */
    public static String getFileUrl(String key) {
        ensureInitialized();
        return getFileUrl(key, staticRegion, staticBucketName);
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

    /**
     * 按前缀列举对象key（自动分页拉取全量）
     *
     * @param prefix 例如 "user-123/knowledge/"
     * @return key 列表（不含目录占位符）
     */
    public static List<String> listKeysByPrefix(String prefix) {
        try {
            ensureInitialized();

            String normalizedPrefix = prefix == null ? "" : prefix.trim();
            List<String> keys = new ArrayList<>();

            String marker = null;
            do {
                ListObjectsRequest request = new ListObjectsRequest();
                request.setBucketName(staticBucketName);
                request.setPrefix(normalizedPrefix);
                request.setMarker(marker);
                request.setMaxKeys(1000); // 单次最多1000，循环翻页拿全量

                ObjectListing listing = cosClient.listObjects(request);

                for (COSObjectSummary summary : listing.getObjectSummaries()) {
                    String key = summary.getKey();
                    // 过滤目录占位符对象（如果有）
                    if (key != null && !key.endsWith("/")) {
                        keys.add(key);
                    }
                }

                marker = listing.isTruncated() ? listing.getNextMarker() : null;
            } while (marker != null);

            return keys;
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("查询文件列表失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("查询文件列表失败：" + e.getMessage(), e);
        }
    }
}
