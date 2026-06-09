package com.zdmj.common.ai;

/**
 * 送入大模型的用户侧文本长度上限（字符数，按 Java {@code String#length()} 计）。
 */
public final class LlmInputLimits {

    /** 对话单条用户消息 */
    public static final int CHAT_MESSAGE_MAX_CHARS = 4000;

    /** 能力画像：粘贴简历纯文本 */
    public static final int RESUME_RAW_TEXT_MAX_CHARS = 50000;

    /** 能力画像：PDF COS URL */
    public static final int RESUME_PDF_URL_MAX_CHARS = 2048;

    private LlmInputLimits() {
    }
}
