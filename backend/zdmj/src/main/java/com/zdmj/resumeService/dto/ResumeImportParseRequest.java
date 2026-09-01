package com.zdmj.resumeService.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 简历导入识别请求（PDF URL 与纯文本二选一）。
 */
@Data
public class ResumeImportParseRequest {

    /**
     * COS 上传后返回的 PDF 文件 URL
     */
    @Size(max = 2048, message = "PDF 链接长度不能超过2048个字符")
    private String pdfUrl;

    /**
     * 简历纯文本（调试或前端自行提取时使用）
     */
    @Size(max = 50000, message = "简历文本长度不能超过50000个字符")
    private String rawText;
}
