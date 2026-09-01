package com.zdmj.common.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class PromptUtil {

    private static final String PREFIX = "classpath:prompts/";
    private static final String SUFFIX = ".md";
    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public PromptUtil(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 按场景与岗位解析提示词名称：专属文件存在则用 {@code {scenario}/{slug}}，否则 {@code {scenario}/default}。
     */
    public String resolve(PromptScenario scenario, JobRole role) {
        if (scenario == null) {
            throw new IllegalArgumentException("PromptScenario must not be null");
        }
        String specific = scenario.path(role);
        return exists(specific) ? specific : scenario.defaultPath();
    }

    /**
     * 加载提示词
     *
     * @param fileName 文件名，如 "system" 或 "job-requirement/java-backend"
     * @return 提示词
     */
    public String load(String fileName) {
        return cache.computeIfAbsent(fileName, this::loadUncached);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cache.clear();
    }

    boolean exists(String fileName) {
        Resource resource = resourceLoader.getResource(PREFIX + fileName + SUFFIX);
        return resource.exists();
    }

    private String loadUncached(String fileName) {
        String location = PREFIX + fileName + SUFFIX;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt not found: " + location);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt: " + location, e);
        }
    }
}
