package com.zdmj.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient配置类
 * 用于配置调用Python服务的HTTP客户端
 */
@Slf4j
@Configuration
public class WebClientConfig {

    private final PythonServiceConfig pythonServiceConfig;

    public WebClientConfig(PythonServiceConfig pythonServiceConfig) {
        this.pythonServiceConfig = pythonServiceConfig;
    }

    /**
     * 创建WebClient Bean
     * 配置超时、重试等策略
     * 
     * @return WebClient实例
     */
    @Bean
    public WebClient pythonServiceWebClient() {
        // 配置HTTP客户端超时
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, pythonServiceConfig.getConnectTimeout())
                .responseTimeout(Duration.ofMillis(pythonServiceConfig.getReadTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(
                                pythonServiceConfig.getReadTimeout(),
                                TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(
                                pythonServiceConfig.getWriteTimeout(),
                                TimeUnit.MILLISECONDS)));

        // 构建WebClient
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(pythonServiceConfig.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json");

        log.info("Python服务WebClient配置完成，基础URL: {}", pythonServiceConfig.getBaseUrl());
        log.info("连接超时: {}ms, 读取超时: {}ms, 写入超时: {}ms",
                pythonServiceConfig.getConnectTimeout(),
                pythonServiceConfig.getReadTimeout(),
                pythonServiceConfig.getWriteTimeout());

        return builder.build();
    }

    /**
     * 创建重试策略
     * 
     * @return Retry策略
     */
    public Retry createRetryStrategy() {
        PythonServiceConfig.RetryConfig retryConfig = pythonServiceConfig.getRetry();

        if (!retryConfig.getEnabled()) {
            return Retry.max(0); // 不重试
        }

        return Retry.fixedDelay(retryConfig.getMaxAttempts() - 1,
                Duration.ofMillis(retryConfig.getRetryInterval()))
                .filter(throwable -> {
                    // 只对特定异常进行重试
                    // 例如：连接超时、读取超时、5xx错误等
                    String message = throwable.getMessage();
                    return message != null && (message.contains("timeout") ||
                            message.contains("Connection refused") ||
                            message.contains("503") ||
                            message.contains("502") ||
                            message.contains("504"));
                })
                .doBeforeRetry(retrySignal -> {
                    log.warn("Python服务调用失败，准备重试。第{}次重试，异常: {}",
                            retrySignal.totalRetries() + 1,
                            retrySignal.failure().getMessage());
                });
    }
}
