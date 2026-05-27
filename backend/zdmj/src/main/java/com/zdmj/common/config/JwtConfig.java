package com.zdmj.common.config;

import com.zdmj.userAuthService.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 从环境变量 / application.yml 注入 JWT 密钥（见项目根目录 .env 中 JWT_SECRET）。
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    void initJwtSecret() {
        JwtUtil.initSecret(jwtSecret);
    }
}
