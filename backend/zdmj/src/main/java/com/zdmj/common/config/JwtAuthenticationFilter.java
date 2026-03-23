package com.zdmj.common.config;

import com.zdmj.common.cache.RedisCacheUtil;
import com.zdmj.common.cache.RedisConstants;
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

    private final RedisCacheUtil redisCacheUtil;

    /**
     * 构造函数注入
     *
     * @param redisCacheUtil Redis缓存工具
     */
    public JwtAuthenticationFilter(RedisCacheUtil redisCacheUtil) {
        this.redisCacheUtil = redisCacheUtil;
    }

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
                        // 检查Redis中是否存在该Token
                        String tokenKey = RedisConstants.JWT_TOKEN_KEY + userId;
                        String storedToken = redisCacheUtil.getString(tokenKey);

                        // 如果Redis中不存在该Token或Token不匹配，说明Token已被删除（如用户登出或重新登录），拒绝访问
                        if (storedToken == null || !storedToken.equals(token)) {
                            log.warn("JWT Token在Redis中不存在或已失效: userId={}, username={}", userId, username);
                            // 不设置认证信息，后续的权限检查会拒绝访问
                        } else {
                            // Token有效，创建用户上下文并存储到ThreadLocal（避免重复解析HTTP请求）
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
                    }
                } catch (Exception e) {
                    log.error("JWT认证失败: {}", e.getMessage());
                }
            }

            // 继续过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 对于异步响应（如SSE），不应该清除SecurityContext，因为响应还在流式传输中
            // 检查请求的Accept头或路径模式来判断是否是SSE端点
            String acceptHeader = request.getHeader("Accept");
            String requestPath = request.getRequestURI();
            boolean isSseByAccept = acceptHeader != null && acceptHeader.contains("text/event-stream");
            // 检查路径：POST /conversations/{id}/messages 或 GET
            // /conversations/{id}/messages/{id}/stream
            boolean isSseByPath = requestPath != null &&
                    (requestPath.contains("/conversations/") && requestPath.contains("/messages") &&
                            (request.getMethod().equals("POST") || requestPath.contains("/stream")));
            boolean isSseResponse = isSseByAccept || isSseByPath;

            // 只有在响应已提交且不是SSE流式响应时，才清除SecurityContext和UserHolder
            // 对于SSE响应，SecurityContext需要在整个流式传输期间保持有效
            if (response.isCommitted() && !isSseResponse) {
                // 请求结束后清除ThreadLocal，避免内存泄漏
                UserHolder.clear();
            }
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
