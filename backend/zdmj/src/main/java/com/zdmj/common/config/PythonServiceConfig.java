package com.zdmj.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Python AI服务配置类
 * 用于配置Python服务的连接信息、超时设置和重试策略
 */
@Configuration
@ConfigurationProperties(prefix = "python")
@Data
public class PythonServiceConfig {

    /**
     * Python服务基础URL
     * 例如: http://localhost:8000
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * 连接超时时间（毫秒）
     * 默认: 5000ms (5秒)
     */
    private Integer connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     * 默认: 30000ms (30秒)，AI处理可能需要较长时间
     */
    private Integer readTimeout = 30000;

    /**
     * 写入超时时间（毫秒）
     * 默认: 10000ms (10秒)
     */
    private Integer writeTimeout = 10000;

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 重试配置内部类
     */
    @Data
    public static class RetryConfig {
        /**
         * 是否启用重试
         * 默认: true
         */
        private Boolean enabled = true;

        /**
         * 最大重试次数
         * 默认: 3次
         */
        private Integer maxAttempts = 3;

        /**
         * 重试间隔（毫秒）
         * 默认: 1000ms (1秒)
         */
        private Long retryInterval = 1000L;
    }
}
