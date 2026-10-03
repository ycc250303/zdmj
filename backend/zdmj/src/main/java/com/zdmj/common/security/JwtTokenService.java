package com.zdmj.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.zdmj.common.util.DateTimeUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 登录令牌的签发与验签。密钥来自 {@code app.jwt.secret}，过期判断使用应用时钟。
 */
@Component
public class JwtTokenService {

    /**
     * 令牌有效期：7 天，与登录 allowlist 的 TTL 一致。
     */
    private static final long EXPIRATION_MILLIS = 7L * 24 * 60 * 60 * 1000L;

    private final SecretKey signingKey;

    /**
     * @param secret {@code JWT_SECRET}，空白时拒绝启动
     */
    public JwtTokenService(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，请在项目根目录 .env 中设置");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发包含用户编号和用户名的令牌。
     *
     * @param userId   用户编号
     * @param username 用户名
     * @return 已签名的令牌
     */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        Date now = Date.from(DateTimeUtil.clock().instant());
        Date expiration = new Date(now.getTime() + EXPIRATION_MILLIS);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 验签并读取声明。签名无效或格式错误时返回 {@code null}。
     *
     * @param token 请求中的令牌
     * @return 声明；无法解析时为 {@code null}
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从令牌读取用户编号。
     *
     * @param token 请求中的令牌
     * @return 用户编号；无法解析时为 {@code null}
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        if (userId instanceof Integer integerId) {
            return integerId.longValue();
        }
        return (Long) userId;
    }

    /**
     * 从令牌读取用户名。
     *
     * @param token 请求中的令牌
     * @return 用户名；无法解析时为 {@code null}
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        return (String) claims.get("username");
    }

    /**
     * 验签并确认令牌未过期。
     *
     * @param token 请求中的令牌
     * @return 签名有效且未过期时为 {@code true}
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读取令牌过期时间。
     *
     * @param token 请求中的令牌
     * @return 过期时间；无法解析时为 {@code null}
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    private boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(Date.from(DateTimeUtil.clock().instant()));
    }
}
