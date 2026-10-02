package com.zdmj.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zdmj.common.util.DateTimeUtil;

/**
 * 应用时钟。测试可以提供 {@code @Primary} 的可变时钟替换本 Bean。
 */
@Configuration
public class TimeConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.system(DateTimeUtil.getDefaultZoneId());
    }
}
