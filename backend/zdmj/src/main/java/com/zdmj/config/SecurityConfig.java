package com.zdmj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类
 */
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

                // 设置Session创建策略为无状态（使用JWT，不需要Session）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 添加JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 配置请求授权
                .authorizeHttpRequests(auth -> auth
                        // 允许匿名访问的接口
                        .requestMatchers(
                                "/api/users", // 用户注册（POST）
                                "/api/users/login", // 用户登录
                                "/api/users/verification-codes", // 发送验证码
                                "/api/users/password", // 重置密码（PUT）
                                "/api/users/validation/**", // 验证用户名/邮箱
                                "/actuator/health", // 健康检查
                                "/actuator/info" // 应用信息
                        ).permitAll()

                        // 其他所有请求需要认证（包括/api/users/{id}等）
                        .anyRequest().authenticated());

        return http.build();
    }
}
