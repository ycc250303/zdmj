package com.zdmj.common.context;

import com.zdmj.exception.BusinessException;

/**
 * 用户信息持有者
 * 使用ThreadLocal存储当前请求的用户信息，避免重复解析HTTP请求
 */
public class UserHolder {

    /**
     * 使用ThreadLocal存储用户上下文信息
     */
    private static final ThreadLocal<UserContext> USER_CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前用户上下文
     * 
     * @param userContext 用户上下文
     */
    public static void set(UserContext userContext) {
        USER_CONTEXT.set(userContext);
    }

    /**
     * 获取当前用户上下文
     * 
     * @return 用户上下文，如果未设置返回null
     */
    public static UserContext get() {
        return USER_CONTEXT.get();
    }

    /**
     * 获取当前用户ID
     * 
     * @return 用户ID，如果未登录返回null
     */
    public static Long getUserId() {
        UserContext context = USER_CONTEXT.get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前用户名
     * 
     * @return 用户名，如果未登录返回null
     */
    public static String getUsername() {
        UserContext context = USER_CONTEXT.get();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 获取当前用户邮箱
     * 
     * @return 邮箱，如果未登录返回null
     */
    public static String getEmail() {
        UserContext context = USER_CONTEXT.get();
        return context != null ? context.getEmail() : null;
    }

    /**
     * 检查当前用户是否已登录
     * 
     * @return 是否已登录
     */
    public static boolean isAuthenticated() {
        return USER_CONTEXT.get() != null;
    }

    /**
     * 要求用户必须已登录，返回用户ID
     * 如果用户未登录，抛出BusinessException异常
     * 
     * @return 用户ID
     * @throws BusinessException 如果用户未登录
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }
        return userId;
    }

    /**
     * 清除当前线程的用户上下文
     * 必须在请求结束后调用，避免内存泄漏
     */
    public static void clear() {
        USER_CONTEXT.remove();
    }
}
