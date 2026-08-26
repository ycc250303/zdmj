package com.zdmj.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.zdmj.common.model.Result;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * 通用文件上传控制器
 * 提供统一的文件上传接口，支持所有模块使用
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "文件上传", description = "通用文件上传、下载与删除")
public class FileUploadController {

    private final FileUploadUtil fileUploadService;

    /**
     * 上传文件到COS（后端直传）
     *
     * @param file   上传文件
     * @param prefix 业务区域前缀，如 knowledge、resume
     * @return 上传结果（key、url、fileName、fileSize、contentType）
     */
    @PostMapping("/upload")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prefix", required = false) String prefix) {
        FileUploadResponse response = fileUploadService.uploadFile(file, prefix);
        return Result.success("文件上传成功", response);
    }

    /**
     * 按 COS key 删除文件
     *
     * @param key COS对象键
     * @return 结果
     */
    @DeleteMapping
    public Result<Void> deleteByKey(@RequestParam("key") String key) {
        fileUploadService.deleteByKey(key);
        return Result.success("文件删除成功", null);
    }

    /**
     * 查询当前用户某业务域下上传的文件列表
     * 例：GET /files/list?prefix=knowledge
     *
     * @param prefix 业务区域
     * @return 文件列表（key、url、fileName、bizArea）
     */
    @GetMapping("/list")
    public Result<List<FileUploadListItemResponse>> listByBizArea(
            @RequestParam(value = "prefix", required = false) String prefix) {
        return Result.success(fileUploadService.listUploadedFiles(prefix));
    }

}
