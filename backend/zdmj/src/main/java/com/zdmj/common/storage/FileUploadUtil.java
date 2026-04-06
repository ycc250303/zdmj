package com.zdmj.common.storage;

import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.CosUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用文件上传服务
 * 提供统一的文件上传功能，支持自定义路径前缀
 */
@Slf4j
@Service
public class FileUploadUtil {
    /**
     * 直传后端上传文件到COS
     */
    public FileUploadResult uploadFile(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String key = buildUserScopedKey(prefix, file.getOriginalFilename());
        String uploadedKey = CosUtil.uploadFile(file, key);
        String fileUrl = CosUtil.getFileUrl(uploadedKey);
        return FileUploadResult.builder()
                .key(uploadedKey)
                .url(fileUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    /**
     * 删除文件
     * 
     * @param key COS对象键
     */
    public void deleteByKey(String key) {
        validateUserKey(key);
        CosUtil.deleteFile(key);
        log.info("文件删除成功，key={}", key);
    }

    /**
     * 查询当前用户某业务域文件列表
     */
    public List<Map<String, String>> listUploadedFiles(String prefix) {
        Long userId = UserHolder.requireUserId();
        String queryPrefix = (prefix == null || prefix.isBlank())
                ? String.format("user-%d/", userId)
                : String.format("user-%d/%s/", userId, sanitizeBizArea(prefix));
        List<String> keys = CosUtil.listKeysByPrefix(queryPrefix); // 需要 CosUtil 提供该方法
        List<Map<String, String>> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("url", CosUtil.getFileUrl(key));
            row.put("fileName", extractFileName(key));
            row.put("bizArea", extractBizArea(key, userId));

            result.add(row);
        }
        return result;
    }

    /**
     * 清理业务区域
     * 
     * @param prefix 业务区域
     * @return 清理后的业务区域
     */
    private String sanitizeBizArea(String prefix) {
        String bizArea = (prefix == null || prefix.isBlank()) ? "files" : prefix.trim();
        return bizArea.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * 构建用户业务域key
     * 
     * @param prefix           业务区域
     * @param originalFilename 原始文件名
     * @return 用户业务域key
     */
    private String buildUserScopedKey(String prefix, String originalFilename) {
        Long userId = UserHolder.requireUserId();
        String bizArea = sanitizeBizArea(prefix);
        String finalPrefix = String.format("user-%d/%s", userId, bizArea);
        return CosUtil.generateKey(finalPrefix, originalFilename);
    }

    /**
     * 验证用户key
     * 
     * @param key COS对象键
     */
    private void validateUserKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Long userId = UserHolder.requireUserId();
        String expectedPrefix = "user-" + userId + "/";
        if (!key.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    /**
     * 提取文件名
     * 
     * @param key COS对象键
     * @return 文件名
     */
    private String extractFileName(String key) {
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    /**
     * 提取业务区域
     * 
     * @param key    COS对象键
     * @param userId 用户ID
     * @return 业务区域
     */
    private String extractBizArea(String key, Long userId) {
        // key 结构: user-{userId}/{bizArea}/{fileName}
        String prefix = "user-" + userId + "/";
        if (!key.startsWith(prefix))
            return "";
        String remain = key.substring(prefix.length());
        int slash = remain.indexOf('/');
        return slash > 0 ? remain.substring(0, slash) : "";
    }
}
