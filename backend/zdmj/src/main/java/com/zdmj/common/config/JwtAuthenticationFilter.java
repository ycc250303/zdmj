package com.zdmj.common.config;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.ProblemDetailHttpWriter;
import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.userAuthService.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * JWT 认证过滤器：验签后与 Redis allowlist 比对。Redis 故障写 503 并中断链路。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtSessionStore jwtSessionStore;
    private final ProblemDetailHttpWriter problemDetailHttpWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && JwtUtil.validateToken(token)) {
            Long userId = JwtUtil.getUserIdFromToken(token);
            String username = JwtUtil.getUsernameFromToken(token);

            if (userId != null && username != null) {
                try {
                    Optional<String> storedToken = jwtSessionStore.find(userId);
                    if (storedToken.isPresent() && storedToken.get().equals(token)) {
                        UserContext userContext = UserContext.of(userId, username);
                        UserHolder.set(userContext);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userContext,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("JWT Token在Redis中不存在或已失效: userId={}, username={}", userId, username);
                    }
                } catch (DataAccessException e) {
                    log.error("登录状态服务不可用: userId={}", userId, e);
                    problemDetailHttpWriter.write(response, ErrorCode.AUTH_STORE_UNAVAILABLE);
                    return;
                }
            } else {
                log.warn("JWT Token解析用户信息失败: userId={}, username={}", userId, username);
            }
        }
        filterChain.doFilter(request, response);
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
        return StringUtils.hasText(bearerToken) ? bearerToken : null;
    }
}
