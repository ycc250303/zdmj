package com.zdmj.common.util;

import com.zdmj.common.model.FileUploadResult;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传服务
 * 提供统一的文件上传功能，支持自定义路径前缀
 */
@Slf4j
@Service
public class FileUploadUtil {

    /**
     * 上传文件到COS
     * 
     * @param file   文件对象
     * @param prefix 路径前缀（可选），如 "knowledge"、"resume" 等
     *               如果不提供，则使用 "files" 作为默认前缀
     * @return 文件上传结果，包含key、url等信息
     * @throws BusinessException 如果文件为空或上传失败
     */
    public FileUploadResult uploadFile(MultipartFile file, String prefix) {
        // 验证文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        // 获取当前用户ID
        Long userId = UserHolder.requireUserId();

        // 确定路径前缀
        String finalPrefix = (prefix != null && !prefix.isEmpty())
                ? prefix + "-" + userId
                : "files-" + userId;

        // 生成文件路径（对象键）
        String key = CosUtil.generateKey(finalPrefix, file.getOriginalFilename());

        // 上传文件到COS
        String uploadedKey = CosUtil.uploadFile(file, key);

        // 获取文件访问URL
        String fileUrl = CosUtil.getFileUrl(uploadedKey);

        // 构建返回结果
        FileUploadResult result = FileUploadResult.builder()
                .key(uploadedKey)
                .url(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();

        log.info("文件上传成功，key: {}, url: {}, fileName: {}", uploadedKey, fileUrl, file.getOriginalFilename());

        return result;
    }

    /**
     * 上传文件到COS（使用默认前缀 "files"）
     * 
     * @param file 文件对象
     * @return 文件上传结果
     */
    public FileUploadResult uploadFile(MultipartFile file) {
        return uploadFile(file, null);
    }
}
