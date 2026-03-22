package com.zdmj.python.service;

import com.zdmj.common.config.PythonServiceConfig;
import com.zdmj.common.config.WebClientConfig;
import com.zdmj.python.constant.PythonErrorCode;
import com.zdmj.python.dto.PythonApiRequest;
import com.zdmj.python.dto.PythonApiResponse;
import com.zdmj.common.exception.PythonServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;

/**
 * Python服务HTTP客户端
 * 提供调用Python服务的统一接口
 */
@Slf4j
@Service
public class PythonServiceClient {

    private static final String DEBUG_LOG_PATH = "debug-4b2fab.log";
    private final WebClient webClient;
    private final PythonServiceConfig pythonServiceConfig;
    private final WebClientConfig webClientConfig;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public PythonServiceClient(WebClient pythonServiceWebClient,
            PythonServiceConfig pythonServiceConfig,
            WebClientConfig webClientConfig) {
        this.webClient = pythonServiceWebClient;
        this.pythonServiceConfig = pythonServiceConfig;
        this.webClientConfig = webClientConfig;
    }

    /**
     * 将响应数据转换为指定类型
     * 处理 Spring WebClient 可能将嵌套泛型反序列化为 LinkedHashMap 的问题
     *
     * @param data  响应数据对象
     * @param clazz 目标类型
     * @param <R>   目标类型
     * @return 转换后的对象，如果转换失败返回原对象
     */
    private <R> R convertResponseData(Object data, Class<R> clazz) {
        if (data == null) {
            return null;
        }

        // 如果已经是正确类型，直接返回
        if (clazz.isInstance(data)) {
            return clazz.cast(data);
        }

        // 如果是 LinkedHashMap，需要转换
        if (data instanceof LinkedHashMap) {
            try {
                return objectMapper.convertValue(data, clazz);
            } catch (Exception e) {
                log.warn("Python服务响应数据转换失败: expected={}, error={}",
                        clazz.getSimpleName(), e.getMessage());
                // 转换失败时返回 null，让调用方处理
                return null;
            }
        }

        // 其他类型，尝试转换
        try {
            return objectMapper.convertValue(data, clazz);
        } catch (Exception e) {
            log.warn("Python服务响应数据转换失败: expected={}, actual={}, error={}",
                    clazz.getSimpleName(), data.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 通用POST请求方法
     * 
     * @param uri     请求URI（相对于baseUrl）
     * @param request 请求体
     * @param clazz   响应类型
     * @param <T>     请求类型
     * @param <R>     响应数据类型
     * @return 响应数据
     */
    public <T extends PythonApiRequest, R> Mono<PythonApiResponse<R>> post(
            String uri, T request, Class<R> clazz) {
        return post(uri, request, clazz, true);
    }

    /**
     * 通用POST请求方法（支持是否重试）
     * 
     * @param uri     请求URI（相对于baseUrl）
     * @param request 请求体
     * @param clazz   响应类型
     * @param retry   是否重试
     * @param <T>     请求类型
     * @param <R>     响应数据类型
     * @return 响应数据
     */
    public <T extends PythonApiRequest, R> Mono<PythonApiResponse<R>> post(
            String uri, T request, Class<R> clazz, boolean retry) {

        log.debug("调用Python服务: POST {}, 请求: {}", uri, request);
        // #region agent log
        appendDebugLog("H2", "PythonServiceClient.post request start", String.format(
                "{\"uri\":\"%s\",\"requestType\":\"%s\",\"retry\":%s,\"baseUrl\":\"%s\"}",
                safe(uri), safe(request == null ? null : request.getClass().getSimpleName()), retry,
                safe(pythonServiceConfig.getBaseUrl())));
        // #endregion

        Mono<PythonApiResponse<R>> responseMono = webClient.post()
                .uri(uri)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("Python服务返回错误状态: {}, URI: {}",
                            clientResponse.statusCode(), uri);
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Python服务错误响应体: {}", errorBody);
                                return Mono.error(new PythonServiceException(
                                        PythonErrorCode.SERVICE_ERROR.getCode(),
                                        clientResponse.statusCode().value(),
                                        String.format("Python服务返回错误: %s, 响应: %s",
                                                clientResponse.statusCode(), errorBody)));
                            });
                })
                .bodyToMono(new ParameterizedTypeReference<PythonApiResponse<R>>() {
                })
                .map(response -> {
                    // #region agent log
                    appendDebugLog("H3", "PythonServiceClient.post response mapped", String.format(
                            "{\"uri\":\"%s\",\"code\":%s,\"success\":%s,\"hasData\":%s}",
                            safe(uri), response.getCode(), response.isSuccess(), response.getData() != null));
                    // #endregion
                    // 检查响应状态码
                    if (response.getCode() == null || !response.isSuccess()) {
                        String errorMsg = response.getMsg() != null
                                ? response.getMsg()
                                : "Python服务返回失败";
                        throw new PythonServiceException(
                                response.getCode() != null ? response.getCode()
                                        : PythonErrorCode.SERVICE_ERROR.getCode(),
                                errorMsg);
                    }
                    // 转换响应数据（处理 LinkedHashMap 问题）
                    if (response.getData() != null) {
                        R convertedData = convertResponseData(response.getData(), clazz);
                        if (convertedData != null) {
                            response.setData(convertedData);
                        }
                    }
                    return response;
                })
                .onErrorMap(WebClientRequestException.class, ex -> {
                    // 处理请求异常（连接超时、读取超时等）
                    log.error("Python服务请求异常: {}, URI: {}", ex.getMessage(), uri, ex);
                    // #region agent log
                    appendDebugLog("H2", "PythonServiceClient.post request exception", String.format(
                            "{\"uri\":\"%s\",\"exception\":\"%s\",\"message\":\"%s\"}",
                            safe(uri), safe(ex.getClass().getSimpleName()), safe(ex.getMessage())));
                    // #endregion
                    if (ex.getMessage() != null) {
                        if (ex.getMessage().contains("timeout") || ex.getMessage().contains("Timeout")) {
                            return new PythonServiceException(
                                    PythonErrorCode.READ_TIMEOUT.getCode(),
                                    PythonErrorCode.READ_TIMEOUT.getMessage(),
                                    ex);
                        }
                        if (ex.getMessage().contains("Connection refused") ||
                                ex.getMessage().contains("connect")) {
                            return new PythonServiceException(
                                    PythonErrorCode.SERVICE_UNAVAILABLE.getCode(),
                                    PythonErrorCode.SERVICE_UNAVAILABLE.getMessage(),
                                    ex);
                        }
                    }
                    return new PythonServiceException(
                            PythonErrorCode.CONNECTION_TIMEOUT.getCode(),
                            "Python服务连接失败: " + ex.getMessage(),
                            ex);
                })
                .onErrorMap(WebClientResponseException.class, ex -> {
                    // 处理HTTP响应异常
                    log.error("Python服务响应异常: {}, URI: {}", ex.getMessage(), uri, ex);
                    return new PythonServiceException(
                            PythonErrorCode.SERVICE_ERROR.getCode(),
                            ex.getStatusCode().value(),
                            String.format("Python服务返回错误: %s, 响应: %s",
                                    ex.getStatusCode(), ex.getResponseBodyAsString()),
                            ex);
                })
                .onErrorMap(Exception.class, ex -> {
                    // 处理其他异常
                    if (ex instanceof PythonServiceException) {
                        return ex;
                    }
                    log.error("Python服务调用未知异常: {}, URI: {}", ex.getMessage(), uri, ex);
                    // #region agent log
                    appendDebugLog("H4", "PythonServiceClient.post unknown exception", String.format(
                            "{\"uri\":\"%s\",\"exception\":\"%s\",\"message\":\"%s\"}",
                            safe(uri), safe(ex.getClass().getSimpleName()), safe(ex.getMessage())));
                    // #endregion
                    return new PythonServiceException(
                            PythonErrorCode.UNKNOWN_ERROR.getCode(),
                            "Python服务调用失败: " + ex.getMessage(),
                            ex);
                });

        // 如果需要重试，应用重试策略
        if (retry && pythonServiceConfig.getRetry().getEnabled()) {
            Retry retryStrategy = webClientConfig.createRetryStrategy();
            responseMono = responseMono.retryWhen(retryStrategy);
        }

        return responseMono;
    }

    // #region agent log
    private void appendDebugLog(String hypothesisId, String message, String dataJson) {
        try {
            long ts = System.currentTimeMillis();
            String line = String.format(
                    "{\"sessionId\":\"4b2fab\",\"runId\":\"pre-fix\",\"hypothesisId\":\"%s\",\"location\":\"PythonServiceClient.post\",\"message\":\"%s\",\"data\":%s,\"timestamp\":%d}%n",
                    safe(hypothesisId), safe(message), dataJson == null ? "{}" : dataJson, ts);
            Files.writeString(Path.of(DEBUG_LOG_PATH), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    // #endregion

    /**
     * 通用GET请求方法
     * 
     * @param uri   请求URI（相对于baseUrl）
     * @param clazz 响应类型
     * @param <R>   响应数据类型
     * @return 响应数据
     */
    public <R> Mono<PythonApiResponse<R>> get(String uri, Class<R> clazz) {
        return get(uri, clazz, true);
    }

    /**
     * 通用GET请求方法（支持是否重试）
     * 
     * @param uri   请求URI（相对于baseUrl）
     * @param clazz 响应类型
     * @param retry 是否重试
     * @param <R>   响应数据类型
     * @return 响应数据
     */
    public <R> Mono<PythonApiResponse<R>> get(String uri, Class<R> clazz, boolean retry) {
        log.debug("调用Python服务: GET {}", uri);

        Mono<PythonApiResponse<R>> responseMono = webClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("Python服务返回错误状态: {}, URI: {}",
                            clientResponse.statusCode(), uri);
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Python服务错误响应体: {}", errorBody);
                                return Mono.error(new PythonServiceException(
                                        PythonErrorCode.SERVICE_ERROR.getCode(),
                                        clientResponse.statusCode().value(),
                                        String.format("Python服务返回错误: %s, 响应: %s",
                                                clientResponse.statusCode(), errorBody)));
                            });
                })
                .bodyToMono(new ParameterizedTypeReference<PythonApiResponse<R>>() {
                })
                .map(response -> {
                    // 检查响应状态码
                    if (response.getCode() == null || !response.isSuccess()) {
                        String errorMsg = response.getMsg() != null
                                ? response.getMsg()
                                : "Python服务返回失败";
                        throw new PythonServiceException(
                                response.getCode() != null ? response.getCode()
                                        : PythonErrorCode.SERVICE_ERROR.getCode(),
                                errorMsg);
                    }
                    // 转换响应数据（处理 LinkedHashMap 问题）
                    if (response.getData() != null) {
                        R convertedData = convertResponseData(response.getData(), clazz);
                        if (convertedData != null) {
                            response.setData(convertedData);
                        }
                    }
                    return response;
                })
                .onErrorMap(WebClientRequestException.class, ex -> {
                    log.error("Python服务请求异常: {}, URI: {}", ex.getMessage(), uri, ex);
                    if (ex.getMessage() != null &&
                            (ex.getMessage().contains("timeout") || ex.getMessage().contains("Timeout"))) {
                        return new PythonServiceException(
                                PythonErrorCode.READ_TIMEOUT.getCode(),
                                PythonErrorCode.READ_TIMEOUT.getMessage(),
                                ex);
                    }
                    return new PythonServiceException(
                            PythonErrorCode.CONNECTION_TIMEOUT.getCode(),
                            "Python服务连接失败: " + ex.getMessage(),
                            ex);
                })
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("Python服务响应异常: {}, URI: {}", ex.getMessage(), uri, ex);
                    return new PythonServiceException(
                            PythonErrorCode.SERVICE_ERROR.getCode(),
                            ex.getStatusCode().value(),
                            String.format("Python服务返回错误: %s, 响应: %s",
                                    ex.getStatusCode(), ex.getResponseBodyAsString()),
                            ex);
                })
                .onErrorMap(Exception.class, ex -> {
                    if (ex instanceof PythonServiceException) {
                        return ex;
                    }
                    log.error("Python服务调用未知异常: {}, URI: {}", ex.getMessage(), uri, ex);
                    return new PythonServiceException(
                            PythonErrorCode.UNKNOWN_ERROR.getCode(),
                            "Python服务调用失败: " + ex.getMessage(),
                            ex);
                });

        // 如果需要重试，应用重试策略
        if (retry && pythonServiceConfig.getRetry().getEnabled()) {
            Retry retryStrategy = webClientConfig.createRetryStrategy();
            responseMono = responseMono.retryWhen(retryStrategy);
        }

        return responseMono;
    }

    /**
     * 健康检查
     * 
     * @return 健康状态
     */
    public Mono<Boolean> healthCheck() {
        return webClient.get()
                .uri("/api/health")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    log.debug("Python服务健康检查响应: {}", response);
                    return true;
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(false)
                .doOnError(ex -> log.warn("Python服务健康检查失败: {}", ex.getMessage()));
    }
}
