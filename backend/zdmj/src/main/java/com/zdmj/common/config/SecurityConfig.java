package com.zdmj.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security 配置类
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 构造函数注入（推荐方式）
     *
     * @param jwtAuthenticationFilter JWT认证过滤器
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（前后端分离项目通常不需要）
                .csrf(csrf -> csrf.disable())

                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 设置Session创建策略为无状态（使用JWT，不需要Session）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 添加JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 配置请求授权
                .authorizeHttpRequests(auth -> auth
                        // 允许匿名访问的接口
                        .requestMatchers(
                                "/api/zdmj/users", // 用户注册（POST）
                                "/api/zdmj/users/login", // 用户登录
                                "/api/zdmj/users/verification-codes", // 发送验证码
                                "/api/zdmj/users/password", // 重置密码（PUT）
                                "/api/zdmj/users/validation/**", // 验证用户名/邮箱
                                "/actuator/health", // 健康检查
                                "/actuator/info" // 应用信息
                        ).permitAll()

                        // 对于异步分发（ASYNC dispatcher），允许所有请求
                        // 这可以避免 "Unable to handle the Spring Security Exception because the response is
                        // already committed" 错误
                        // 异步分发发生在响应提交后，此时无法再发送错误响应
                        .requestMatchers(request -> request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC)
                        .permitAll()

                        // 其他所有请求需要认证（包括/api/zdmj/users/{id}等）
                        .anyRequest().authenticated())

                // 配置异常处理：对于已提交的响应，完全跳过异常处理
                // 这可以避免 "Unable to handle the Spring Security Exception because the response is
                // already committed" 错误
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 如果响应已提交，完全跳过处理，不尝试发送任何响应
                            if (response.isCommitted()) {
                                log.debug("响应已提交，跳过认证异常处理");
                                return;
                            }
                            try {
                                response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
                            } catch (Exception e) {
                                // 如果响应在检查后提交，忽略异常
                                log.debug("无法发送认证错误响应: {}", e.getMessage());
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 如果响应已提交，完全跳过处理，不尝试发送任何响应
                            if (response.isCommitted()) {
                                log.debug("响应已提交，跳过授权异常处理");
                                return;
                            }
                            try {
                                response.setStatus(org.springframework.http.HttpStatus.FORBIDDEN.value());
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"code\":403,\"message\":\"Access Denied\"}");
                            } catch (Exception e) {
                                // 如果响应在检查后提交，忽略异常
                                log.debug("无法发送授权错误响应: {}", e.getMessage());
                            }
                        }));

        return http.build();
    }

    /**
     * CORS配置源
     * 允许跨域请求，解决前后端分离项目的跨域问题
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许所有源（生产环境建议配置具体的前端域名）
        // 注意：当 allowCredentials 为 true 时，不能使用 "*"，需要使用具体的域名或 OriginPatterns
        configuration.setAllowedOriginPatterns(List.of("*"));

        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 允许的请求头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许发送凭证（如Cookie）
        // 注意：当使用 setAllowedOriginPatterns("*") 时，allowCredentials 必须为 false
        // 如果需要发送凭证，请使用具体的域名列表，例如：configuration.setAllowedOrigins(List.of("http://localhost:3000"))
        configuration.setAllowCredentials(false);

        // 预检请求的缓存时间（秒）
        configuration.setMaxAge(3600L);

        // 暴露的响应头
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
