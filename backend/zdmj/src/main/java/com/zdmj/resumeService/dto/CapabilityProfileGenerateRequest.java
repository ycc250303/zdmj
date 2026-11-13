package com.zdmj.resumeService.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 能力画像生成的纯文本请求。上传文件走同一路径的 multipart 请求。
 */
@Data
public class CapabilityProfileGenerateRequest {

    /**
     * 简历纯文本。空白文本会被拒绝。
     */
    @Size(max = 50000, message = "简历文本长度不能超过50000个字符")
    private String rawText;
}
