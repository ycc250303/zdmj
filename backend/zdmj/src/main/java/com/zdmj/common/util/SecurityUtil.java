package com.zdmj.common.util;

import com.zdmj.common.context.UserHolder;

/**
 * 安全工具类
 * 用于获取当前登录用户信息
 * 
 * @deprecated 推荐使用 {@link UserHolder} 来获取用户信息，性能更好
 */
@Deprecated
public class SecurityUtil {

    /**
     * 获取当前登录用户ID
     * 
     * @return 用户ID，如果未登录返回null
     */
    public static Long getCurrentUserId() {
        // 优先从UserHolder获取（性能更好）
        Long userId = UserHolder.getUserId();
        if (userId != null) {
            return userId;
        }
        // 兼容旧代码，从SecurityContext获取
        return null;
    }

    /**
     * 获取当前登录用户名
     * 
     * @return 用户名，如果未登录返回null
     */
    public static String getCurrentUsername() {
        // 优先从UserHolder获取（性能更好）
        String username = UserHolder.getUsername();
        if (username != null) {
            return username;
        }
        return null;
    }

    /**
     * 检查当前用户是否已登录
     * 
     * @return 是否已登录
     */
    public static boolean isAuthenticated() {
        return UserHolder.isAuthenticated();
    }
}
