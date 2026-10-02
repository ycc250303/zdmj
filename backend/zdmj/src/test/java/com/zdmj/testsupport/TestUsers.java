package com.zdmj.testsupport;

import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.mapper.UserMapper;
import com.zdmj.userAuthService.util.PasswordUtil;

/**
 * 创建两名普通用户。系统没有独立角色表，登录后过滤器授予 ROLE_USER。
 */
public class TestUsers {

    public static final String PASSWORD = "test-password";

    private final UserMapper userMapper;

    public TestUsers(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 插入用户 A 与用户 B。主键由数据库生成。
     */
    public Pair createPair() {
        return new Pair(
                insert("user-a", "user-a@example.com", "用户A"),
                insert("user-b", "user-b@example.com", "用户B"));
    }

    private User insert(String username, String email, String name) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setName(name);
        user.setPassword(PasswordUtil.encode(PASSWORD));
        userMapper.insert(user);
        if (user.getId() == null) {
            throw new IllegalStateException("插入用户后未回填主键: " + username);
        }
        return user;
    }

    public record Pair(User userA, User userB) {
    }
}
