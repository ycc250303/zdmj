package com.zdmj.common.controller;

import com.zdmj.common.Result;
import com.zdmj.common.model.FileUploadResult;
import com.zdmj.common.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传控制器
 * 提供统一的文件上传接口，支持所有模块使用
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    /**
     * 上传文件到COS
     * 
     * @param file 文件对象（必填）
     * @param prefix 路径前缀（可选），用于区分不同业务模块
     *               例如：knowledge（知识库）、resume（简历）、project（项目）等
     *               如果不提供，默认使用 "files" 作为前缀
     * @return 上传结果，包含文件key、访问URL等信息
     * 
     * 使用示例：
     * 1. 知识库文档上传：POST /files/upload?prefix=knowledge
     * 2. 简历文件上传：POST /files/upload?prefix=resume
     * 3. 通用文件上传：POST /files/upload
     */
    @PostMapping("/upload")
    public Result<FileUploadResult> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prefix", required = false) String prefix) {
        FileUploadResult result = fileUploadService.uploadFile(file, prefix);
        return Result.success("文件上传成功", result);
    }
}
