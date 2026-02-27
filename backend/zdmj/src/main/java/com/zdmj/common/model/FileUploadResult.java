package com.zdmj.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果DTO
 * 统一返回文件上传后的相关信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResult {
    
    /**
     * 文件在COS中的对象键（key）
     */
    private String key;
    
    /**
     * 文件访问URL（永久URL）
     */
    private String url;
    
    /**
     * 原始文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件内容类型（MIME类型）
     */
    private String contentType;
}
