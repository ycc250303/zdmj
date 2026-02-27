package com.zdmj.common.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
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
 * // 上传文件流
 * String key = CosUtil.uploadStream(inputStream, "path/file.pdf", "application/pdf", fileSize);
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
    private static Long staticPresignedUrlExpiration;

    /**
     * 初始化COS客户端
     */
    @PostConstruct
    public void init() {
        try {
            // 验证配置
            if (secretId == null || secretId.isEmpty() || "your-secret-id".equals(secretId)) {
                log.warn("COS SecretId未配置或使用默认值，请设置环境变量 COS_SECRET_ID");
            }
            if (secretKey == null || secretKey.isEmpty() || "your-secret-key".equals(secretKey)) {
                log.warn("COS SecretKey未配置或使用默认值，请设置环境变量 COS_SECRET_KEY");
            }
            if (bucketName == null || bucketName.isEmpty()) {
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
            staticPresignedUrlExpiration = presignedUrlExpiration;

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
     * 格式：{prefix}/{原始文件名（不含扩展名）}-{timestamp}-{uuid}.{extension}
     * 
     * @param prefix           路径前缀，如 "project/docs"
     * @param originalFilename 原始文件名
     * @return 文件路径（对象键）
     */
    public static String generateKey(String prefix, String originalFilename) {
        String extension = "";
        String fileNameWithoutExt = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            int lastDotIndex = originalFilename.lastIndexOf(".");
            extension = originalFilename.substring(lastDotIndex);
            fileNameWithoutExt = originalFilename.substring(0, lastDotIndex);
        } else if (originalFilename != null) {
            fileNameWithoutExt = originalFilename;
        }

        // 清理文件名，移除特殊字符，只保留字母、数字、中文、下划线、连字符
        if (fileNameWithoutExt != null && !fileNameWithoutExt.isEmpty()) {
            fileNameWithoutExt = fileNameWithoutExt.replaceAll("[^\\w\\u4e00-\\u9fa5-]", "_");
        }

        String uuid = UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();

        // 构建文件名：{原始文件名}-{timestamp}-{uuid}.{extension}
        String fileName;
        if (fileNameWithoutExt != null && !fileNameWithoutExt.isEmpty()) {
            fileName = String.format("%s-%d-%s%s", fileNameWithoutExt, timestamp, uuid, extension);
        } else {
            fileName = String.format("%d-%s%s", timestamp, uuid, extension);
        }

        if (prefix != null && !prefix.isEmpty()) {
            // 确保prefix不以/结尾
            if (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            return String.format("%s/%s", prefix, fileName);
        } else {
            return fileName;
        }
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
     * 
     * @param file 文件对象
     * @param key  文件路径（对象键），如果为null则自动生成
     * @return 文件路径（对象键）
     * @throws RuntimeException 上传失败时抛出
     */
    public static String uploadFile(MultipartFile file, String key) {
        try {
            // 如果没有指定key，则自动生成
            if (key == null || key.isEmpty()) {
                key = generateKey(file.getOriginalFilename());
            }

            // 创建上传请求
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    staticBucketName,
                    key,
                    file.getInputStream(),
                    metadata);

            // 上传文件
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);

            log.info("文件上传成功，key: {}, ETag: {}", key, putObjectResult.getETag());

            return key;
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}，状态码：{}，请求ID：{}",
                    e.getErrorCode(), e.getErrorMessage(), e.getStatusCode(), e.getRequestId(), e);
            // 提供更详细的错误提示
            String errorMsg = "文件上传失败：" + e.getErrorMessage();
            if ("InvalidRequest".equals(e.getErrorCode())) {
                errorMsg += "。请检查：1) 存储桶名称格式是否正确（应为 bucketname-appid）；2) 存储桶是否存在；3) 地域配置是否正确；4) SecretId和SecretKey是否正确";
            }
            throw new RuntimeException(errorMsg, e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("文件上传异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传文件流到COS
     * 
     * @param inputStream   文件输入流
     * @param key           文件路径（对象键）
     * @param contentType   文件类型，如 "application/pdf"
     * @param contentLength 文件大小（字节），可为null
     * @return 文件路径（对象键）
     * @throws RuntimeException 上传失败时抛出
     */
    public static String uploadStream(InputStream inputStream, String key, String contentType, Long contentLength) {
        try {
            // 创建上传请求
            ObjectMetadata metadata = new ObjectMetadata();
            if (contentLength != null) {
                metadata.setContentLength(contentLength);
            }
            if (contentType != null) {
                metadata.setContentType(contentType);
            }

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    staticBucketName,
                    key,
                    inputStream,
                    metadata);

            // 上传文件
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);

            log.info("文件流上传成功，key: {}, ETag: {}", key, putObjectResult.getETag());

            return key;
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("文件上传失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("文件流上传异常", e);
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
     * 生成预签名上传URL
     * 
     * @param key        文件路径（对象键）
     * @param expiration 过期时间（秒），如果为null则使用配置的默认值
     * @return 预签名URL
     * @throws RuntimeException 生成失败时抛出
     */
    public static String generatePresignedUploadUrl(String key, Long expiration) {
        try {
            if (expiration == null) {
                expiration = staticPresignedUrlExpiration;
            }

            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    staticBucketName,
                    key,
                    HttpMethodName.PUT);
            request.setExpiration(new Date(System.currentTimeMillis() + expiration * 1000));

            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        } catch (CosServiceException e) {
            log.error("COS服务异常，错误码：{}，错误消息：{}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("生成预签名上传URL失败：" + e.getErrorMessage(), e);
        } catch (CosClientException e) {
            log.error("COS客户端异常", e);
            throw new RuntimeException("生成预签名上传URL失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成预签名上传URL（使用默认过期时间）
     * 
     * @param key 文件路径（对象键）
     * @return 预签名URL
     */
    public static String generatePresignedUploadUrl(String key) {
        return generatePresignedUploadUrl(key, null);
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
        if (staticRegion == null || staticBucketName == null) {
            throw new IllegalStateException("COS工具类未初始化，请确保CosUtil已被Spring容器管理");
        }
        return getFileUrl(key, staticRegion, staticBucketName);
    }
}
