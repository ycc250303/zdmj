package com.zdmj.testsupport;

import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.common.security.JwtTokenService;

/**
 * 生成 JWT 并写入测试容器中的登录 allowlist。
 */
public class TestJwt {

    private final JwtSessionStore jwtSessionStore;
    private final JwtTokenService jwtTokenService;

    public TestJwt(JwtSessionStore jwtSessionStore, JwtTokenService jwtTokenService) {
        this.jwtSessionStore = jwtSessionStore;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 为用户签发令牌并写入 Session Store。
     *
     * @param user 已持久化的用户
     * @return 令牌
     */
    public String authorize(User user) {
        if (user.getId() == null) {
            throw new IllegalStateException("用户主键为空，不能签发令牌");
        }
        String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
        jwtSessionStore.replace(user.getId(), token);
        return token;
    }
}
