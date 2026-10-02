package com.zdmj.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 测试用时钟。未指定时刻时跟随系统时钟，指定后所有 {@code instant()} 返回该时刻。
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixed;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("固定时刻不能为空");
        }
        this.fixed = instant;
    }

    public void resetToSystem() {
        this.fixed = null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock copy = new MutableClock(zone);
        copy.fixed = this.fixed;
        return copy;
    }

    @Override
    public Instant instant() {
        return fixed == null ? Clock.system(zone).instant() : fixed;
    }
}
