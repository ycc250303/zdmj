package com.zdmj.common.util;

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
     * 加载提示词
     * 
     * @return 提示词
     * @param fileName 文件名，如 "system.md"
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

    /**
     * 加载提示词
     * 
     * @param fileName 文件名，如 "system.md"
     * @return 提示词
     */
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

    public final class PromptNames {
        private PromptNames() {
        }

        /** 通用对话 system */
        public static final String SYSTEM = "system";
        /** 生成会话标题 */
        public static final String GENERATE_CONVERSATION_TITLE = "generate-conversation-title";
        /** 项目上下文说明 */
        public static final String PROJECT_CONTEXT_INSTRUCTION = "project-context-instruction";
    }
}
