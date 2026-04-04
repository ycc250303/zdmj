package com.zdmj.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "python.api")
public class PythonApiConfig {

    @Value("${python.api.base-url}")
    private String baseUrl;

    @Value("${python.api.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${python.api.read-timeout-ms}")
    private int readTimeoutMs;

    @Value("${python.api.max-retries}")
    private int maxRetries;

    @Value("${python.api.retry-backoff-ms}")
    private int retryBackoffMs;

    @Value("${python.api.poll-interval-ms}")
    private int pollIntervalMs;

    @Value("${python.api.poll-timeout-ms}")
    private int pollTimeoutMs;

}
