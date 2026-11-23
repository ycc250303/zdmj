package com.zdmj.resumeService.dto;

import jakarta.validation.constraints.Size;

/**
 * 简历导入识别的纯文本请求。上传文件走同一路径的 multipart 请求。
 *
 * @param rawText 简历纯文本。空白文本会被拒绝。
 */
public record ResumeImportParseRequest(
        @Size(max = 50000, message = "简历文本长度不能超过50000个字符")
        String rawText) {
}
