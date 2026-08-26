package com.zdmj.common.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件列表项响应（GET /files/list 元素结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadListItemResponse {

    /** COS 对象键 */
    private String key;

    /** 文件访问 URL */
    private String url;

    /** 文件名 */
    private String fileName;

    /** 业务区域（user-{userId}/ 后的首段路径） */
    private String bizArea;
}
