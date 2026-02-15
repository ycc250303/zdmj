package com.zdmj.common.context;

import lombok.Data;

/**
 * 用户上下文信息
 * 存储当前登录用户的完整信息
 */
@Data
public class UserContext {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 创建用户上下文
     * 
     * @param userId   用户ID
     * @param username 用户名
     * @return 用户上下文
     */
    public static UserContext of(Long userId, String username) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(username);
        return context;
    }

    /**
     * 创建用户上下文（包含邮箱）
     * 
     * @param userId   用户ID
     * @param username 用户名
     * @param email    邮箱
     * @return 用户上下文
     */
    public static UserContext of(Long userId, String username, String email) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(username);
        context.setEmail(email);
        return context;
    }
}
