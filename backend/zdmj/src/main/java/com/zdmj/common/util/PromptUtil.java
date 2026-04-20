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
        /** 生成能力画像 */
        public static final String GENERATE_CAPABILITY_PROFILE = "generate-capability-profile";
        /** 生成岗位能力画像（旧版通用提示词） */
        public static final String GENERATE_JOB_CAPABILITY_PROFILE = "generate-job-capability-profile";
        /** 岗位要求画像 Java 后端 */
        public static final String JOB_REQUIREMENT_JAVA_BACKEND = "job-requirement/job-requirement-java-backend";
        /** 岗位要求画像 前端 */
        public static final String JOB_REQUIREMENT_FRONTEND = "job-requirement/job-requirement-frontend";
        /** 岗位要求画像 C/C++ */
        public static final String JOB_REQUIREMENT_CPP = "job-requirement/job-requirement-cpp";
        /** 岗位要求画像 软件测试 */
        public static final String JOB_REQUIREMENT_SOFTWARE_TEST = "job-requirement/job-requirement-software-test";
        /** 岗位要求画像 默认兜底 */
        public static final String JOB_REQUIREMENT_DEFAULT = "job-requirement/job-requirement-default";
        /** 知识库 RAG 问答 */
        public static final String KNOWLEDGEBASE_RAG_SYSTEM = "knowledgebase-rag-system";
        /** 知识库查询改写 */
        public static final String KNOWLEDGEBASE_RAG_QUERY_REWRITE = "knowledgebase-query-rewrite";
        /** 简历分析 Java 后端 */
        public static final String RESUME_ANALYSIS_JAVA_BACKEND = "resume-analysis-java-backend";
        /** 简历分析 前端 */
        public static final String RESUME_ANALYSIS_FRONTEND = "resume-analysis-frontend";
        /** 简历分析 C/C++ */
        public static final String RESUME_ANALYSIS_CPP = "resume-analysis-cpp";
        /** 简历分析 软件测试 */
        public static final String RESUME_ANALYSIS_SOFTWARE_TEST = "resume-analysis-software-test";
        /** 岗位分类 job-detect */
        public static final String JOB_DETECT = "job-detect";
    }

    public enum JobRole {
        JAVA,
        FRONTEND,
        CPP,
        SOFTWARE_TEST,
        UNKNOWN
    }

    /**
     * 将 LLM/配置中的 roleCode 归一化为 JobRole（兼容 java_backend、backend、大小写等）
     */
    public static JobRole getJobRoleByString(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return JobRole.UNKNOWN;
        }
        String key = roleCode.trim().toLowerCase();
        return switch (key) {
            case "java", "java_backend", "backend" -> JobRole.JAVA;
            case "frontend", "fe", "web_frontend" -> JobRole.FRONTEND;
            case "cpp", "c++", "c_cpp" -> JobRole.CPP;
            case "software_test", "test", "qa" -> JobRole.SOFTWARE_TEST;
            case "unknown", "default" -> JobRole.UNKNOWN;
            default -> JobRole.UNKNOWN;
        };
    }

    /**
     * 岗位角色 -> PromptName 映射
     */
    public static String getResumeAnalysisPromptName(JobRole role) {
        if (role == null) {
            return PromptNames.GENERATE_CAPABILITY_PROFILE;
        }
        return switch (role) {
            case JAVA -> PromptNames.RESUME_ANALYSIS_JAVA_BACKEND;
            case FRONTEND -> PromptNames.RESUME_ANALYSIS_FRONTEND;
            case CPP -> PromptNames.RESUME_ANALYSIS_CPP;
            case SOFTWARE_TEST -> PromptNames.RESUME_ANALYSIS_SOFTWARE_TEST;
            case UNKNOWN -> PromptNames.GENERATE_CAPABILITY_PROFILE;
        };
    }

    /**
     * 岗位角色 -> 岗位要求画像 PromptName 映射
     */
    public static String getJobRequirementPromptName(JobRole role) {
        if (role == null) {
            return PromptNames.JOB_REQUIREMENT_DEFAULT;
        }
        return switch (role) {
            case JAVA -> PromptNames.JOB_REQUIREMENT_JAVA_BACKEND;
            case FRONTEND -> PromptNames.JOB_REQUIREMENT_FRONTEND;
            case CPP -> PromptNames.JOB_REQUIREMENT_CPP;
            case SOFTWARE_TEST -> PromptNames.JOB_REQUIREMENT_SOFTWARE_TEST;
            case UNKNOWN -> PromptNames.JOB_REQUIREMENT_DEFAULT;
        };
    }
}
