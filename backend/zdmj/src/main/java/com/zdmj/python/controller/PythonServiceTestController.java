package com.zdmj.python.controller;

import com.zdmj.common.Result;
import com.zdmj.python.service.PythonServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Python服务测试控制器
 * 用于测试Java与Python服务之间的通信
 */
@Slf4j
@RestController
@RequestMapping("/python/test")
public class PythonServiceTestController {

    private final PythonServiceClient pythonServiceClient;

    public PythonServiceTestController(PythonServiceClient pythonServiceClient) {
        this.pythonServiceClient = pythonServiceClient;
    }

    /**
     * 测试Python服务健康检查接口
     * 调用Python服务的健康检查接口，验证服务通信是否正常
     * 
     * @return 健康检查结果
     */
    @GetMapping("/health")
    public Mono<Result<Map<String, Object>>> testPythonHealth() {
        log.info("开始测试Python服务健康检查接口");

        return pythonServiceClient.healthCheck()
                .map(healthy -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("pythonServiceHealthy", healthy);
                    result.put("message", healthy ? "Python服务健康检查通过" : "Python服务健康检查失败");

                    log.info("Python服务健康检查结果: {}", healthy);

                    if (healthy) {
                        return Result.success("Python服务通信正常", result);
                    } else {
                        return new Result<>(500, "Python服务通信异常", result);
                    }
                })
                .onErrorResume(ex -> {
                    log.error("Python服务健康检查异常", ex);
                    Map<String, Object> result = new HashMap<>();
                    result.put("pythonServiceHealthy", false);
                    result.put("error", ex.getMessage());
                    result.put("message", "Python服务健康检查异常: " + ex.getMessage());
                    return Mono.just(new Result<>(500, "Python服务健康检查异常: " + ex.getMessage(), result));
                });
    }

    /**
     * 同步方式测试Python服务健康检查接口
     * 使用block()方法阻塞等待结果（不推荐在生产环境使用，仅用于测试）
     * 
     * @return 健康检查结果
     */
    @GetMapping("/health/sync")
    public Result<Map<String, Object>> testPythonHealthSync() {
        log.info("开始测试Python服务健康检查接口（同步方式）");

        try {
            Boolean healthy = pythonServiceClient.healthCheck()
                    .block(); // 阻塞等待结果

            Map<String, Object> result = new HashMap<>();
            result.put("pythonServiceHealthy", healthy != null && healthy);
            result.put("message", (healthy != null && healthy)
                    ? "Python服务健康检查通过"
                    : "Python服务健康检查失败");

            log.info("Python服务健康检查结果: {}", healthy);

            if (healthy != null && healthy) {
                return Result.success("Python服务通信正常", result);
            } else {
                return new Result<>(500, "Python服务通信异常", result);
            }
        } catch (Exception ex) {
            log.error("Python服务健康检查异常", ex);
            Map<String, Object> result = new HashMap<>();
            result.put("pythonServiceHealthy", false);
            result.put("error", ex.getMessage());
            result.put("message", "Python服务健康检查异常: " + ex.getMessage());
            return new Result<>(500, "Python服务健康检查异常: " + ex.getMessage(), result);
        }
    }
}
