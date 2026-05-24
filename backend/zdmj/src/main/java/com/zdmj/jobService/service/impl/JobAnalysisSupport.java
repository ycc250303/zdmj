package com.zdmj.jobService.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.PromptUtil;
import com.zdmj.common.ai.prompt.PromptNames;
import com.zdmj.common.ai.PromptUtil.JobRole;
import com.zdmj.jobService.dto.JobListItemDTO;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

public final class JobAnalysisSupport {

    private JobAnalysisSupport() {
    }

    public static JobRole detectRole(
            String text,
            ChatUtil chatUtil,
            Map<JobRole, List<String>> keywords,
            int keywordDirectHitThreshold,
            Logger log) {
        if (!StringUtils.hasText(text)) {
            return JobRole.UNKNOWN;
        }
        String lower = text.toLowerCase();
        JobRole bestRole = JobRole.UNKNOWN;
        int bestScore = 0;

        for (Map.Entry<JobRole, List<String>> entry : keywords.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestRole = entry.getKey();
            }
        }

        if (bestScore >= keywordDirectHitThreshold) {
            return bestRole;
        }

        try {
            RoleDetectLLMResult llmResult = chatUtil.chatStructuredOnce(
                    text, PromptNames.JOB_DETECT, null, RoleDetectLLMResult.class);
            JobRole role = PromptUtil.getJobRoleByString(llmResult.getRoleCode());
            return role == JobRole.UNKNOWN ? bestRole : role;
        } catch (Exception e) {
            log.warn("岗位类型识别失败，回退关键词规则: {}", e.getMessage());
            return bestRole;
        }
    }

    public static double estimateRoleConfidence(JobRole role, String text, Map<JobRole, List<String>> keywords) {
        if (role == null || role == JobRole.UNKNOWN || !StringUtils.hasText(text)) {
            return 0.2;
        }
        String lower = text.toLowerCase();
        int hit = 0;
        for (String kw : keywords.getOrDefault(role, List.of())) {
            if (lower.contains(kw.toLowerCase())) {
                hit++;
            }
        }
        return Math.min(0.95, 0.35 + hit * 0.1);
    }

    public static String buildJobContext(JobListItemDTO job, String intro) {
        return """
                %s
                岗位名称：%s
                公司名称：%s
                工作地点：%s
                薪资：%s
                岗位描述：%s
                岗位职责：%s
                岗位要求：%s
                关键词：%s
                公司行业：%s
                """.formatted(
                intro,
                valueOrNA(job.getJobName()),
                valueOrNA(job.getCompanyName()),
                valueOrNA(job.getLocation()),
                valueOrNA(job.getSalary()),
                valueOrNA(job.getDescription()),
                joinList(job.getJobDuties()),
                joinList(job.getJobRequirements()),
                joinList(job.getKeywords()),
                joinList(job.getCompanyIndustries()));
    }

    public static String toJson(Object value, ObjectMapper objectMapper, Logger log, String warnMessage) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("{}: {}", warnMessage, e.getMessage());
            return null;
        }
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "未提供";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((a, b) -> a + "；" + b)
                .orElse("未提供");
    }

    private static String valueOrNA(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未提供";
    }

    @lombok.Data
    private static class RoleDetectLLMResult {
        private String roleCode;
        private double confidence;
        private String reason;
    }
}
