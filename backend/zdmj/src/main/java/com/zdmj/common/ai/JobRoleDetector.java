package com.zdmj.common.ai;

import com.zdmj.common.ai.prompt.PromptNames;
import lombok.Data;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

/**
 * 岗位方向识别：关键词直出，不足则 LLM {@code job-detect} 兜底。
 *
 * <p>仅简历画像与岗位画像生成时调用；图谱 / 匹配 / 生涯报告读已落库的
 * {@code targetRoleType}，不再二次识别。</p>
 */
public final class JobRoleDetector {

    static final int KEYWORD_DIRECT_HIT_THRESHOLD = 4;

    private JobRoleDetector() {
    }

    public static DetectResult detect(Long userId, String text, ChatUtil chatUtil, Logger log) {
        if (!StringUtils.hasText(text)) {
            return new DetectResult(JobRole.UNKNOWN, 0.0, "文本为空");
        }

        ScoredRole scored = scoreByKeywords(text);
        if (scored.score() >= KEYWORD_DIRECT_HIT_THRESHOLD) {
            double conf = Math.min(0.9, 0.45 + scored.score() * 0.1);
            return new DetectResult(scored.role(), conf, "关键词规则命中: " + scored.score());
        }

        try {
            RoleDetectLLMResult llmResult = chatUtil.chatStructuredOnce(
                    userId, text, PromptNames.JOB_DETECT, null, RoleDetectLLMResult.class);
            JobRole role = JobRole.fromString(llmResult.getRoleCode());
            if (role == JobRole.UNKNOWN && scored.role() != JobRole.UNKNOWN) {
                return new DetectResult(scored.role(), 0.45,
                        "LLM 返回 unknown，采用关键词弱命中: " + scored.score());
            }
            double confidence = llmResult.getConfidence();
            String reason = StringUtils.hasText(llmResult.getReason()) ? llmResult.getReason() : "LLM 分类";
            return new DetectResult(role, confidence, reason);
        } catch (Exception e) {
            if (log != null) {
                log.warn("岗位类型识别失败，回退关键词规则: {}", e.getMessage());
            }
            if (scored.role() != JobRole.UNKNOWN) {
                return new DetectResult(scored.role(), 0.35, "LLM 失败，回退关键词弱命中");
            }
            return new DetectResult(JobRole.UNKNOWN, 0.2, "规则与 LLM 均未明确岗位");
        }
    }

    private static ScoredRole scoreByKeywords(String text) {
        String lower = text.toLowerCase();
        JobRole bestRole = JobRole.UNKNOWN;
        int bestScore = 0;
        for (JobRole role : JobRole.values()) {
            if (role == JobRole.UNKNOWN) {
                continue;
            }
            int score = 0;
            for (String kw : role.keywords()) {
                if (lower.contains(kw.toLowerCase())) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestRole = role;
            }
        }
        return new ScoredRole(bestRole, bestScore);
    }

    public record DetectResult(JobRole role, double confidence, String reason) {
    }

    private record ScoredRole(JobRole role, int score) {
    }

    @Data
    public static class RoleDetectLLMResult {
        private String roleCode;
        private double confidence;
        private String reason;
    }
}
