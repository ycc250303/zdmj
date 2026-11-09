package com.zdmj.jobService.service.impl;

import com.zdmj.jobService.api.JobRole;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import lombok.Data;
import org.springframework.stereotype.Service;
import com.zdmj.jobService.api.JobRoleDetectionService;
import com.zdmj.jobService.api.JobRoleDetectionResult;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 岗位方向识别：关键词达到阈值时返回结果，其余文本通过模型识别。
 *
 * <p>仅简历画像与岗位画像生成时调用。其他流程读取已落库的 {@code targetRoleType}。</p>
 */
@Service
public final class JobRoleDetectionServiceImpl implements JobRoleDetectionService {

    private static final String DETECT_PROMPT = "job-detect";
    private final ModelGateway modelGateway;

    static final int KEYWORD_DIRECT_HIT_THRESHOLD = 4;

    public JobRoleDetectionServiceImpl(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public JobRoleDetectionResult detect(CurrentActor actor, String text) {
        java.util.Objects.requireNonNull(actor, "操作者不能为空");
        if (!StringUtils.hasText(text)) {
            return new JobRoleDetectionResult(JobRole.UNKNOWN, 0.0, "文本为空");
        }

        ScoredRole scored = scoreByKeywords(text);
        if (scored.score() >= KEYWORD_DIRECT_HIT_THRESHOLD) {
            double conf = Math.min(0.9, 0.45 + scored.score() * 0.1);
            return new JobRoleDetectionResult(scored.role(), conf, "关键词规则命中: " + scored.score());
        }

        RoleDetectLLMResult result = modelGateway.generateStructured(actor,
                new ModelRequest(text, DETECT_PROMPT, null), RoleDetectLLMResult.class);
        if (result == null || !StringUtils.hasText(result.getRoleCode())) {
            throw new IllegalStateException("模型未返回岗位方向");
        }
        String code = result.getRoleCode().trim().toLowerCase(Locale.ROOT);
        JobRole role = JobRole.fromString(code);
        if (role == JobRole.UNKNOWN && !code.equals("unknown") && !code.equals("default")) {
            throw new IllegalStateException("模型返回非法岗位方向");
        }
        Double confidence = result.getConfidence();
        if (confidence == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalStateException("模型返回的置信度必须为 [0, 1] 内的有限数值");
        }
        String reason = StringUtils.hasText(result.getReason()) ? result.getReason() : "模型分类";
        return new JobRoleDetectionResult(role, confidence, reason);
    }

    private static ScoredRole scoreByKeywords(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        JobRole bestRole = JobRole.UNKNOWN;
        int bestScore = 0;
        for (JobRole role : JobRole.values()) {
            if (role == JobRole.UNKNOWN) {
                continue;
            }
            int score = 0;
            for (String kw : role.keywords()) {
                if (containsKeyword(lower, kw)) {
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

    /**
     * ASCII 词按单词边界匹配，避免 {@code java} 误伤 {@code javascript}；中文词仍用包含。
     */
    static boolean containsKeyword(String lowerText, String keyword) {
        String kw = keyword.toLowerCase(Locale.ROOT);
        if (isAsciiKeyword(kw)) {
            int from = 0;
            while (true) {
                int idx = lowerText.indexOf(kw, from);
                if (idx < 0) {
                    return false;
                }
                if (isAsciiBoundary(lowerText, idx, idx + kw.length())) {
                    return true;
                }
                from = idx + 1;
            }
        }
        return lowerText.contains(kw);
    }

    private static boolean isAsciiKeyword(String kw) {
        for (int i = 0; i < kw.length(); i++) {
            if (kw.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiBoundary(String text, int start, int end) {
        if (start > 0 && isAsciiWordChar(text.charAt(start - 1))) {
            return false;
        }
        return end >= text.length() || !isAsciiWordChar(text.charAt(end));
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private record ScoredRole(JobRole role, int score) {
    }

    /** 模型结构化响应，仅供岗位识别实现解析。 */
    @Data
    public static class RoleDetectLLMResult {
        /** 模型返回的规范方向代码，允许 unknown。 */
        private String roleCode;
        /** 必填置信度，必须为 [0, 1] 内的有限数值。 */
        private Double confidence;
        /** 模型给出的识别依据。 */
        private String reason;
    }
}
