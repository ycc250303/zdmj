package com.zdmj.common.ai;

/**
 * 触发大模型推理的 HTTP 接口限流阈值（{@link com.zdmj.common.annotation.RateLimit} USER 维度，每分钟）。
 */
public final class LlmRateLimits {

    /** 流式对话 {@code POST /messages/chat} */
    public static final double CHAT_PER_MIN = 30;

    /** 能力画像生成 */
    public static final double CAPABILITY_PROFILE_GENERATE_PER_MIN = 10;

    /** 简历 PDF/文本结构化识别 */
    public static final double RESUME_IMPORT_PARSE_PER_MIN = 10;

    /** 人岗匹配生成 */
    public static final double MATCH_GENERATE_PER_MIN = 10;

    /** 职业发展报告生成（重任务） */
    public static final double CAREER_REPORT_GENERATE_PER_MIN = 5;

    /** 职业发展报告润色 / 完整性复核 */
    public static final double CAREER_REPORT_AUX_PER_MIN = 10;

    /** 岗位能力画像生成 */
    public static final double JOB_CAPABILITY_PROFILE_PER_MIN = 10;

    /** 岗位关联图谱生成（重任务） */
    public static final double JOB_CAREER_GRAPH_PER_MIN = 5;

    private LlmRateLimits() {
    }
}
