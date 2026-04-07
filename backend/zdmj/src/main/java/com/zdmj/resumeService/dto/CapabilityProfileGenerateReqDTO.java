package com.zdmj.resumeService.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 画像生成请求参数 DTO
 * 支持 PDF URL 或者纯文本直接传入
 */
@Data
public class CapabilityProfileGenerateReqDTO {

    /**
     * COS 上传后返回的 PDF 文件 URL
     * 和 rawText 二选一即可
     */
    private String pdfUrl;

    /**
     * 前端自行拼接的文本内容
     * 和 pdfUrl 二选一即可
     */
    private String rawText;

}
