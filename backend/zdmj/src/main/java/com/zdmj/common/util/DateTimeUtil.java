package com.zdmj.common.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

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
    
    /**
     * 结束日期早于开始日期时拒绝写入。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param errorCode 结束日期早于开始日期时抛出的业务错误码
     * @throws BusinessException 结束日期早于开始日期
     */
    public static void rejectIfEndBeforeStart(LocalDate startDate, LocalDate endDate, ErrorCode errorCode) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(errorCode);
        }
    }
}
