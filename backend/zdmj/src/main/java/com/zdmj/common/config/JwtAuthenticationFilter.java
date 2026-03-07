package com.zdmj.common.config;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.userAuthService.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 从请求头中获取Token
            String token = getTokenFromRequest(request);

            if (StringUtils.hasText(token) && JwtUtil.validateToken(token)) {
                try {
                    // 从Token中获取用户信息
                    Long userId = JwtUtil.getUserIdFromToken(token);
                    String username = JwtUtil.getUsernameFromToken(token);

                    if (userId != null && username != null) {
                        // 创建用户上下文并存储到ThreadLocal（避免重复解析HTTP请求）
                        UserContext userContext = UserContext.of(userId, username);
                        UserHolder.set(userContext);

                        // 创建认证对象
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userId, // principal
                                null, // credentials
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")) // authorities
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // 设置到Security上下文
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("JWT认证成功: userId={}, username={}", userId, username);
                    }
                } catch (Exception e) {
                    log.error("JWT认证失败: {}", e.getMessage());
                }
            }

            // 继续过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清除ThreadLocal，避免内存泄漏
            UserHolder.clear();
        }
    }

    /**
     * 从请求头中获取Token
     * 支持两种格式：
     * 1. Authorization: Bearer <token>
     * 2. Authorization: <token>
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 兼容没有Bearer前缀的情况
        if (StringUtils.hasText(bearerToken)) {
            return bearerToken;
        }
        return null;
    }
}
