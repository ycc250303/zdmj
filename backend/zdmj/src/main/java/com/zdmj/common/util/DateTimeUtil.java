package com.zdmj.common.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 应用时间。业务时间以 Asia/Shanghai 的 {@link LocalDateTime} 表示，来源为可替换的 {@link Clock}。
 */
public final class DateTimeUtil {

    /**
     * 应用默认时区：Asia/Shanghai（UTC+8）
     */
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static volatile Clock clock = Clock.system(DEFAULT_ZONE_ID);

    private DateTimeUtil() {
    }

    /**
     * 获取当前时间（基于应用默认时区）。
     *
     * @return 当前时间的 LocalDateTime（Asia/Shanghai）
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * 当前时钟。JWT 过期判断与自动填充共用这一实例。
     */
    public static Clock clock() {
        return clock;
    }

    /**
     * 绑定应用时钟。启动时由 {@code ApplicationClockBinder} 注入容器中的时钟。
     *
     * @param applicationClock 非空时钟，时区应使用 {@link #getDefaultZoneId()}
     */
    public static void bind(Clock applicationClock) {
        if (applicationClock == null) {
            throw new IllegalArgumentException("应用时钟不能为空");
        }
        clock = applicationClock;
    }

    /**
     * 获取当前时区 ID。
     *
     * @return Asia/Shanghai
     */
    public static ZoneId getDefaultZoneId() {
        return DEFAULT_ZONE_ID;
    }
}
