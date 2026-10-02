package com.zdmj.common.config;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.zdmj.common.util.DateTimeUtil;

/**
 * 启动时把容器中的 {@link Clock} 绑定到 {@link DateTimeUtil}，供静态调用方使用。
 */
@Component
public class ApplicationClockBinder {

    public ApplicationClockBinder(Clock clock) {
        DateTimeUtil.bind(clock);
    }
}
