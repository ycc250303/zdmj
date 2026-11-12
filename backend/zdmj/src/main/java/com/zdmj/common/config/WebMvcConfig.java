package com.zdmj.common.config;

import java.util.List;

import com.zdmj.common.web.CurrentActorArgumentResolver;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * 统一配置 Controller 的 API 前缀与操作者参数解析。
 */
@Configuration
@Import(CurrentActorArgumentResolver.class)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentActorArgumentResolver currentActorArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentActorArgumentResolver);
    }

    @Value("${api.prefix:/api}")
    private String apiPrefix;

    /**
     * 配置路径匹配，为所有 Controller 添加统一前缀
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(apiPrefix,
                c -> c.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class));
    }
}
