package com.zdmj.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
/**
 * 方法级限流。同一方法可重复标注，所有规则都通过才放行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.List.class)
public @interface RateLimit {
    Dimension dimension() default Dimension.GLOBAL;

    /** 时间窗口内允许的最大请求数 */
    double count();

    /** 时间窗口大小 */
    long interval() default 1;
    
    /** 时间单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
    
    /** 限流维度 */ 
    enum Dimension {
        GLOBAL, IP, USER
    }
    
    /** 可重复注解容器 */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        RateLimit[] value();
    }
}