package com.zdmj.conversationService.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.conversationService.dto.VectorRetrievalResult;
import com.zdmj.conversationService.service.ProjectRAGService;
import com.zdmj.conversationService.service.VectorRetrievalService;
import com.zdmj.resumeService.entity.ProjectExperience;
import com.zdmj.resumeService.service.ProjectExperienceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目RAG服务实现类
 * 实现项目上下文检索逻辑，包括项目信息获取、多维度查询构建、检索结果格式化等
 */
@Slf4j
@Service
public class ProjectRAGServiceImpl implements ProjectRAGService {

    private final ProjectExperienceService projectExperienceService;
    private final VectorRetrievalService vectorRetrievalService;
    private final ObjectMapper objectMapper;

    // 检索参数
    private static final int KNOWLEDGE_TOP_K = 5;
    private static final double KNOWLEDGE_MIN_SCORE = 0.3; // 降低阈值，从0.6改为0.3，提高召回率
    private static final int CODE_TOP_K = 5;
    private static final double CODE_MIN_SCORE = 0.3;

    public ProjectRAGServiceImpl(
            ProjectExperienceService projectExperienceService,
            VectorRetrievalService vectorRetrievalService,
            ObjectMapper objectMapper) {
        this.projectExperienceService = projectExperienceService;
        this.vectorRetrievalService = vectorRetrievalService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String retrieveProjectContext(Long projectId, Long userId, String userMessage) {
        // 参数校验
        if (projectId == null) {
            log.warn("项目ID为空，返回空上下文");
            return "";
        }
        if (userId == null) {
            log.warn("用户ID为空，返回空上下文");
            return "";
        }

        try {
            // 1. 获取项目信息（带错误处理）
            ProjectExperience project = getProjectWithErrorHandling(projectId, userId);
            if (project == null) {
                return "";
            }

            // 2. 检索项目基础信息相关的知识库向量
            Map<String, List<VectorRetrievalResult>> knowledgeResults = retrieveKnowledgeVectors(project, projectId);

            // 3. 检索项目代码向量（分为两部分：基础信息相关 + 用户输入相关）
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"before retrieveProjectCodeVectors\",\"data\":{\"projectId\":%d,\"projectName\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 74, projectId,
                        project != null ? project.getName() : "null"));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            List<VectorRetrievalResult> codeResults = retrieveProjectCodeVectors(project, projectId, userMessage);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"after retrieveProjectCodeVectors\",\"data\":{\"projectId\":%d,\"codeResultCount\":%d},\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 75, projectId,
                        codeResults != null ? codeResults.size() : 0));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion

            // 4. 格式化检索结果为XML格式
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"before formatProjectContext\",\"data\":{\"projectId\":%d,\"knowledgeResultSize\":%d,\"codeResultSize\":%d},\"runId\":\"run1\",\"hypothesisId\":\"H\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 77, projectId, knowledgeResults.size(),
                        codeResults != null ? codeResults.size() : 0));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            String context = formatProjectContext(project, knowledgeResults, codeResults);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                // 检查是否包含代码部分
                boolean hasCodeSection = context.contains("<项目代码参考>");
                int codeSectionStart = context.indexOf("<项目代码参考>");
                String codeSectionPreview = codeSectionStart >= 0
                        ? context.substring(codeSectionStart, Math.min(codeSectionStart + 500, context.length()))
                        : "NOT_FOUND";
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"after formatProjectContext\",\"data\":{\"projectId\":%d,\"contextLength\":%d,\"hasCodeSection\":%s,\"codeSectionStart\":%d,\"codeSectionPreview\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"H\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 110, projectId, context.length(),
                        hasCodeSection, codeSectionStart,
                        codeSectionPreview.replace("\n", "\\n").replace("\"", "\\\"").replace("\r", "")));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            return context;

        } catch (BusinessException e) {
            // 业务异常（如项目不存在、无权限等）已在上层处理，这里记录日志
            log.warn("检索项目上下文时发生业务异常: projectId={}, userId={}, error={}",
                    projectId, userId, e.getMessage());
            return "";
        } catch (Exception e) {
            log.error("检索项目上下文失败: projectId={}, userId={}", projectId, userId, e);
            // 错误降级：返回空上下文，不影响对话流程
            return "";
        }
    }

    /**
     * 获取项目信息，带完善的错误处理
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 项目信息，如果不存在或无权访问返回null
     */
    private ProjectExperience getProjectWithErrorHandling(Long projectId, Long userId) {
        try {
            ProjectExperience project = projectExperienceService.getById(projectId);

            // 验证项目所有权
            if (project != null && !project.getUserId().equals(userId)) {
                log.warn("用户无权访问该项目: projectId={}, userId={}, projectUserId={}",
                        projectId, userId, project.getUserId());
                return null;
            }

            return project;
        } catch (BusinessException e) {
            // 项目不存在等业务异常
            log.warn("项目不存在或无权访问: projectId={}, userId={}, error={}",
                    projectId, userId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("获取项目信息失败: projectId={}, userId={}", projectId, userId, e);
            return null;
        }
    }

    /**
     * 检索知识库向量
     * 根据项目信息构建多个查询维度，分别检索相关知识库文档
     * 带完善的错误处理，单个维度检索失败不影响其他维度
     *
     * @param project   项目信息
     * @param projectId 项目ID
     * @return 按维度分组的检索结果
     */
    private Map<String, List<VectorRetrievalResult>> retrieveKnowledgeVectors(
            ProjectExperience project, Long projectId) {
        Map<String, List<VectorRetrievalResult>> results = new LinkedHashMap<>();

        // 1. 技术栈相关文档检索
        if (project.getTechStack() != null && !project.getTechStack().isEmpty()) {
            try {
                String techStackQuery = String.join(", ", project.getTechStack());
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                    fw.write(String.format(
                            "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"retrieving tech stack vectors\",\"data\":{\"projectId\":%d,\"query\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), 204, projectId,
                            techStackQuery.substring(0, Math.min(50, techStackQuery.length()))));
                    fw.close();
                } catch (Exception e) {
                }
                // #endregion
                List<VectorRetrievalResult> techStackResults = vectorRetrievalService.retrieveKnowledgeVectors(
                        techStackQuery, projectId, null, KNOWLEDGE_TOP_K, KNOWLEDGE_MIN_SCORE);
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                    fw.write(String.format(
                            "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"tech stack vectors retrieved\",\"data\":{\"projectId\":%d,\"resultCount\":%d},\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), 206, projectId,
                            techStackResults != null ? techStackResults.size() : 0));
                    fw.close();
                } catch (Exception e) {
                }
                // #endregion
                if (techStackResults != null && !techStackResults.isEmpty()) {
                    results.put("技术栈相关文档", techStackResults);
                }
            } catch (Exception e) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                    fw.write(String.format(
                            "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"tech stack retrieval exception\",\"data\":{\"projectId\":%d,\"error\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"F\"}\n",
                            System.currentTimeMillis(), System.currentTimeMillis(), 210, projectId, e.getMessage()));
                    fw.close();
                } catch (Exception e2) {
                }
                // #endregion
                log.warn("检索技术栈相关文档失败: projectId={}, techStack={}",
                        projectId, project.getTechStack(), e);
                // 继续执行其他维度的检索
            }
        }

        // 2. 项目描述相关文档检索
        if (project.getDescription() != null && !project.getDescription().trim().isEmpty()) {
            try {
                List<VectorRetrievalResult> descriptionResults = vectorRetrievalService.retrieveKnowledgeVectors(
                        project.getDescription(), projectId, null, KNOWLEDGE_TOP_K, KNOWLEDGE_MIN_SCORE);
                if (descriptionResults != null && !descriptionResults.isEmpty()) {
                    results.put("项目描述相关文档", descriptionResults);
                }
            } catch (Exception e) {
                log.warn("检索项目描述相关文档失败: projectId={}", projectId, e);
                // 继续执行其他维度的检索
            }
        }

        // 3. 核心贡献相关文档检索
        if (project.getContribution() != null && !project.getContribution().trim().isEmpty()) {
            try {
                List<VectorRetrievalResult> contributionResults = vectorRetrievalService.retrieveKnowledgeVectors(
                        project.getContribution(), projectId, null, KNOWLEDGE_TOP_K, KNOWLEDGE_MIN_SCORE);
                if (contributionResults != null && !contributionResults.isEmpty()) {
                    results.put("核心贡献相关文档", contributionResults);
                }
            } catch (Exception e) {
                log.warn("检索核心贡献相关文档失败: projectId={}", projectId, e);
                // 继续执行其他维度的检索
            }
        }

        // 4. 项目亮点相关文档检索
        try {
            List<String> highlightQueries = parseHighlights(project.getHighlights());
            for (int i = 0; i < highlightQueries.size(); i++) {
                String highlightQuery = highlightQueries.get(i);
                if (highlightQuery != null && !highlightQuery.trim().isEmpty()) {
                    try {
                        List<VectorRetrievalResult> highlightResults = vectorRetrievalService.retrieveKnowledgeVectors(
                                highlightQuery, projectId, null, KNOWLEDGE_TOP_K, KNOWLEDGE_MIN_SCORE);
                        if (highlightResults != null && !highlightResults.isEmpty()) {
                            results.put("项目亮点\"" + highlightQuery + "\"相关文档", highlightResults);
                        }
                    } catch (Exception e) {
                        log.warn("检索项目亮点相关文档失败: projectId={}, highlight={}",
                                projectId, highlightQuery, e);
                        // 继续执行其他亮点的检索
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析项目亮点失败: projectId={}", projectId, e);
            // 继续执行，不影响其他检索
        }

        return results;
    }

    /**
     * 检索项目代码向量
     * 根据项目亮点和用户消息检索相关代码
     * 带完善的错误处理，单个查询失败不影响其他查询
     *
     * @param project     项目信息
     * @param projectId   项目ID
     * @param userMessage 用户消息
     * @return 代码检索结果列表
     */
    private List<VectorRetrievalResult> retrieveProjectCodeVectors(
            ProjectExperience project, Long projectId, String userMessage) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
            fw.write(String.format(
                    "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"retrieveProjectCodeVectors entry\",\"data\":{\"projectId\":%d,\"projectName\":\"%s\",\"userMessage\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                    System.currentTimeMillis(), System.currentTimeMillis(), 259, projectId,
                    project != null ? project.getName() : "null",
                    userMessage != null ? userMessage.substring(0, Math.min(50, userMessage.length())) : "null"));
            fw.close();
        } catch (Exception e) {
        }
        // #endregion
        List<VectorRetrievalResult> allCodeResults = new ArrayList<>();
        Set<String> seenContent = new HashSet<>(); // 用于去重

        // 1. 根据项目亮点检索代码（这部分可以缓存，但为了简化，暂时不缓存）
        try {
            List<String> highlightQueries = parseHighlights(project.getHighlights());
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log", true);
                fw.write(String.format(
                        "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"highlight queries parsed\",\"data\":{\"projectId\":%d,\"highlightCount\":%d},\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n",
                        System.currentTimeMillis(), System.currentTimeMillis(), 266, projectId,
                        highlightQueries.size()));
                fw.close();
            } catch (Exception e) {
            }
            // #endregion
            for (String highlightQuery : highlightQueries) {
                if (highlightQuery != null && !highlightQuery.trim().isEmpty()) {
                    try {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log",
                                    true);
                            fw.write(String.format(
                                    "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"calling retrieveProjectCodeVectors\",\"data\":{\"projectId\":%d,\"highlightQuery\":\"%s\",\"topK\":%d,\"minScore\":%f},\"runId\":\"run1\",\"hypothesisId\":\"C\"}\n",
                                    System.currentTimeMillis(), System.currentTimeMillis(), 270, projectId,
                                    highlightQuery.substring(0, Math.min(50, highlightQuery.length())), CODE_TOP_K,
                                    CODE_MIN_SCORE));
                            fw.close();
                        } catch (Exception e) {
                        }
                        // #endregion
                        List<VectorRetrievalResult> codeResults = vectorRetrievalService.retrieveProjectCodeVectors(
                                highlightQuery, projectId, CODE_TOP_K, CODE_MIN_SCORE);
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log",
                                    true);
                            fw.write(String.format(
                                    "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"retrieveProjectCodeVectors returned\",\"data\":{\"projectId\":%d,\"resultCount\":%d},\"runId\":\"run1\",\"hypothesisId\":\"C\"}\n",
                                    System.currentTimeMillis(), System.currentTimeMillis(), 272, projectId,
                                    codeResults != null ? codeResults.size() : 0));
                            fw.close();
                        } catch (Exception e) {
                        }
                        // #endregion
                        // 去重：基于content去重
                        if (codeResults != null) {
                            for (VectorRetrievalResult result : codeResults) {
                                if (result != null && result.getContent() != null
                                        && !seenContent.contains(result.getContent())) {
                                    allCodeResults.add(result);
                                    seenContent.add(result.getContent());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // #region agent log
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("d:\\GitHub\\zdmj\\.cursor\\debug.log",
                                    true);
                            fw.write(String.format(
                                    "{\"id\":\"log_%d\",\"timestamp\":%d,\"location\":\"ProjectRAGServiceImpl.java:%d\",\"message\":\"retrieveProjectCodeVectors exception\",\"data\":{\"projectId\":%d,\"error\":\"%s\"},\"runId\":\"run1\",\"hypothesisId\":\"C\"}\n",
                                    System.currentTimeMillis(), System.currentTimeMillis(), 283, projectId,
                                    e.getMessage()));
                            fw.close();
                        } catch (Exception e2) {
                        }
                        // #endregion
                        log.warn("根据项目亮点检索代码失败: projectId={}, highlight={}",
                                projectId, highlightQuery, e);
                        // 继续执行其他亮点的检索
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析项目亮点失败，跳过亮点相关代码检索: projectId={}", projectId, e);
            // 继续执行用户消息相关的检索
        }

        // 2. 根据用户消息检索代码（如果用户消息不为空，这部分不缓存）
        if (userMessage != null && !userMessage.trim().isEmpty()) {
            try {
                List<VectorRetrievalResult> userCodeResults = vectorRetrievalService.retrieveProjectCodeVectors(
                        userMessage, projectId, CODE_TOP_K, CODE_MIN_SCORE);
                // 去重：基于content去重
                if (userCodeResults != null) {
                    for (VectorRetrievalResult result : userCodeResults) {
                        if (result != null && result.getContent() != null
                                && !seenContent.contains(result.getContent())) {
                            allCodeResults.add(result);
                            seenContent.add(result.getContent());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("根据用户消息检索代码失败: projectId={}, userMessage={}",
                        projectId, userMessage, e);
                // 继续执行，返回已有的结果
            }
        }

        // 按分数降序排序
        allCodeResults.sort((a, b) -> {
            double scoreA = a.getScore() != null ? a.getScore() : 0.0;
            double scoreB = b.getScore() != null ? b.getScore() : 0.0;
            return Double.compare(scoreB, scoreA);
        });

        // 限制返回数量
        int maxResults = CODE_TOP_K * 2; // 最多返回10个代码结果
        return allCodeResults.stream().limit(maxResults).collect(Collectors.toList());
    }

    /**
     * 解析项目亮点JSON字符串
     * highlights字段格式：JSONB数组，每个元素包含type和content字段
     *
     * @param highlightsJson JSON字符串
     * @return 亮点内容列表
     */
    private List<String> parseHighlights(String highlightsJson) {
        List<String> highlightContents = new ArrayList<>();
        if (highlightsJson == null || highlightsJson.trim().isEmpty()) {
            return highlightContents;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(highlightsJson);
            if (rootNode.isArray()) {
                for (JsonNode item : rootNode) {
                    if (item.isObject() && item.has("content")) {
                        JsonNode contentNode = item.get("content");
                        if (contentNode.isTextual()) {
                            String content = contentNode.asText();
                            if (content != null && !content.trim().isEmpty()) {
                                highlightContents.add(content);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析项目亮点JSON失败: {}", highlightsJson, e);
        }

        return highlightContents;
    }

    /**
     * 格式化项目上下文为XML格式
     * 参考prisma-ai的实现方式
     *
     * @param project          项目信息
     * @param knowledgeResults 知识库检索结果（按维度分组）
     * @param codeResults      代码检索结果
     * @return XML格式的上下文字符串
     */
    private String formatProjectContext(
            ProjectExperience project,
            Map<String, List<VectorRetrievalResult>> knowledgeResults,
            List<VectorRetrievalResult> codeResults) {
        StringBuilder xml = new StringBuilder();
        xml.append("<项目上下文>\n");

        // 1. 项目基本信息
        xml.append("  <项目基本信息>\n");
        xml.append("    项目名称：").append(project.getName() != null ? project.getName() : "").append("\n");
        if (project.getTechStack() != null && !project.getTechStack().isEmpty()) {
            xml.append("    技术栈：").append(String.join(", ", project.getTechStack())).append("\n");
        }
        if (project.getRole() != null && !project.getRole().trim().isEmpty()) {
            xml.append("    角色：").append(project.getRole()).append("\n");
        }
        if (project.getDescription() != null && !project.getDescription().trim().isEmpty()) {
            xml.append("    描述：").append(project.getDescription()).append("\n");
        }
        if (project.getContribution() != null && !project.getContribution().trim().isEmpty()) {
            xml.append("    核心贡献：").append(project.getContribution()).append("\n");
        }
        xml.append("  </项目基本信息>\n");

        // 2. 项目知识库文档参考
        if (!knowledgeResults.isEmpty()) {
            xml.append("  \n  <项目知识库文档参考>\n");
            for (Map.Entry<String, List<VectorRetrievalResult>> entry : knowledgeResults.entrySet()) {
                String dimension = entry.getKey();
                List<VectorRetrievalResult> results = entry.getValue();
                xml.append("    <").append(dimension).append(">\n");
                for (VectorRetrievalResult result : results) {
                    if (result.getContent() != null && !result.getContent().trim().isEmpty()) {
                        // 转义XML特殊字符
                        String content = escapeXml(result.getContent());
                        xml.append("      ").append(content).append("\n");
                    }
                }
                xml.append("    </").append(dimension).append(">\n");
            }
            xml.append("  </项目知识库文档参考>\n");
        }

        // 3. 项目代码参考
        if (!codeResults.isEmpty()) {
            xml.append("  \n  <项目代码参考>\n");
            for (VectorRetrievalResult result : codeResults) {
                if (result.getContent() != null && !result.getContent().trim().isEmpty()) {
                    // 转义XML特殊字符
                    String content = escapeXml(result.getContent());
                    String filePath = result.getFilePath() != null ? result.getFilePath() : "未知文件";
                    xml.append("    <代码片段>\n");
                    xml.append("      文件路径：").append(filePath).append("\n");
                    xml.append("      代码内容：\n");
                    xml.append("      ").append(content).append("\n");
                    xml.append("    </代码片段>\n");
                }
            }
            xml.append("  </项目代码参考>\n");
        }

        xml.append("</项目上下文>");
        return xml.toString();
    }

    /**
     * 转义XML特殊字符
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
