package com.zdmj.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云COS配置类
 */
@Configuration
@ConfigurationProperties(prefix = "cos")
@Data
public class CosConfig {

    /**
     * 腾讯云SecretId
     */
    private String secretId;

    /**
     * 腾讯云SecretKey
     */
    private String secretKey;

    /**
     * 地域，如：ap-beijing（北京）、ap-shanghai（上海）
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 自定义域名（可选），如果不配置则使用默认域名
     */
    private String domain;

    /**
     * 预签名URL过期时间（秒），默认1小时
     */
    private Long presignedUrlExpiration = 3600L;
}
