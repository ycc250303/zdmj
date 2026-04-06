package com.zdmj.common.config;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class EmbeddingConfig {

    @Bean
    public TextSplitter textSplitter() {
        return new TokenTextSplitter();
    }

    @Bean("embeddingExecutor")
    public Executor embeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); //核心线程数
        executor.setMaxPoolSize(4); //最大线程数
        executor.setQueueCapacity(100); //队列容量
        executor.setThreadNamePrefix("embedding-"); //线程名前缀
        executor.setWaitForTasksToCompleteOnShutdown(true); //等待任务完成
        executor.setAwaitTerminationSeconds(30); //等待时间
        executor.initialize();
        return executor;
    }
}
