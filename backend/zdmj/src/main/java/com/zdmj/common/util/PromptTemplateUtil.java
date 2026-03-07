package com.zdmj.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PromptTemplateUtil {

    private final ResourceLoader resourceLoader;

    private final Map<String, String> templateCache = new HashMap<>();

    public PromptTemplateUtil(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载 Prompt 模板文件
     * 模板文件应放在 resources/prompts/ 目录下
     * 
     * @param templateName 模板名称（不含扩展名，如 "project-mine"）
     * @return 模板内容字符串
     * @throws RuntimeException 如果模板文件不存在或读取失败
     */
    public String loadTemplate(String templateName) {
        // 检查缓存
        if (templateCache.containsKey(templateName)) {
            return templateCache.get(templateName);
        }

        try {
            // 从 resources/prompts/ 目录加载模板
            Resource resource = resourceLoader.getResource("classpath:prompts/" + templateName + ".md");

            if (!resource.exists()) {
                throw new RuntimeException("Prompt 模板不存在: " + templateName);
            }

            // 读取文件内容
            String content = StreamUtils.copyToString(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8);

            // 缓存模板
            templateCache.put(templateName, content);

            log.debug("加载 Prompt 模板成功: {}", templateName);
            return content;
        } catch (IOException e) {
            log.error("加载 Prompt 模板失败: {}", templateName, e);
            throw new RuntimeException("加载 Prompt 模板失败: " + templateName, e);
        }
    }

    /**
     * 构建 Prompt（注入变量）
     * 
     * @param templateContent 模板内容字符串
     * @param variables       变量映射（key-value 对）
     * @return Prompt 对象
     */
    public Prompt buildPrompt(String templateContent, Map<String, Object> variables) {
        try {
            PromptTemplate promptTemplate = new PromptTemplate(templateContent);
            return promptTemplate.create(variables);
        } catch (Exception e) {
            log.error("构建 Prompt 失败", e);
            throw new RuntimeException("构建 Prompt 失败", e);
        }
    }

    /**
     * 便捷方法：加载模板并构建 Prompt
     * 
     * @param templateName 模板名称
     * @param variables    变量映射
     * @return Prompt 对象
     */
    public Prompt loadAndBuildPrompt(String templateName, Map<String, Object> variables) {
        String templateContent = loadTemplate(templateName);
        return buildPrompt(templateContent, variables);
    }

    /**
     * 清除模板缓存（用于开发时重新加载）
     */
    public void clearCache() {
        templateCache.clear();
        log.debug("Prompt 模板缓存已清除");
    }

    /**
     * 清除指定模板的缓存
     */
    public void clearCache(String templateName) {
        templateCache.remove(templateName);
        log.debug("Prompt 模板缓存已清除: {}", templateName);
    }
}
