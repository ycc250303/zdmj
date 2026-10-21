package com.zdmj.common.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/** 读取 prompts 目录内的 UTF-8 提示词，并按相对名称缓存内容。 */
@Component
public class PromptLoader {

    private static final String PREFIX = "classpath:prompts/";
    private static final String SUFFIX = ".md";
    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载提示词
     *
     * @param fileName 文件名，如 "system" 或 "job-requirement/java-backend"
     * @return 提示词
     * @throws IllegalArgumentException 路径非法或提示词文件缺失
     * @throws IllegalStateException 文件读取失败
     */
    public String load(String fileName) {
        if (fileName == null || !fileName.matches("[a-z0-9-]+(?:/[a-z0-9-]+)*")) {
            throw new IllegalArgumentException("提示词名称必须为 prompts 目录内的相对路径");
        }
        return cache.computeIfAbsent(fileName, this::loadUncached);
    }

    private String loadUncached(String fileName) {
        String location = PREFIX + fileName + SUFFIX;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt not found: " + location);
        }
        try (var input = resource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt: " + location, e);
        }
    }
}
