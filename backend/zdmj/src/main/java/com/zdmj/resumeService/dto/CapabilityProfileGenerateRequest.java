package com.zdmj.resumeService.dto;

import lombok.Data;

/**
 * 能力画像生成请求体（PDF URL 与纯文本二选一）。
 */
@Data
public class CapabilityProfileGenerateRequest {

    /**
     * COS 上传后返回的 PDF 文件 URL
     */
    private String pdfUrl;

    /**
     * 前端自行拼接的文本内容
     */
    private String rawText;
}
