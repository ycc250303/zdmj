package com.zdmj.resumeService.dto;

import com.zdmj.common.ai.LlmInputLimits;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 能力画像生成请求体（PDF URL 与纯文本二选一）。
 */
@Data
public class CapabilityProfileGenerateRequest {

    /**
     * COS 上传后返回的 PDF 文件 URL
     */
    @Size(max = LlmInputLimits.RESUME_PDF_URL_MAX_CHARS, message = "PDF 链接长度不能超过2048个字符")
    private String pdfUrl;

    /**
     * 前端自行拼接的文本内容
     */
    @Size(max = LlmInputLimits.RESUME_RAW_TEXT_MAX_CHARS, message = "简历文本长度不能超过50000个字符")
    private String rawText;
}
