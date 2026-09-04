package com.zdmj.common.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PdfParserUtil {
    private static final Tika TIKA = new Tika();

    private final FileUploadService fileUploadService;

    /**
     * 解析当前登录用户拥有的 COS 对象。拒绝任意 http(s)/file URL，避免 SSRF。
     */
    public String extractTextFromUrl(String url) {
        return extractTextFromUrl(url, UserHolder.requireUserId());
    }

    /**
     * 解析指定用户拥有的 COS 对象。异步向量化须传入文档所属 userId。
     */
    public String extractTextFromUrl(String url, Long ownerUserId) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件地址不能为空");
        }
        if (ownerUserId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        String trimmed = url.trim();
        if (!fileUploadService.isManagedCosUrl(trimmed)) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR, "仅支持本系统已上传的文件");
        }
        try (InputStream inputStream = fileUploadService.openInputStreamFromUrl(trimmed, ownerUserId)) {
            return normalizeExtractedText(TIKA.parseToString(inputStream));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从本地文件路径提取 PDF 文本内容。
     * 
     * @param path 文件路径
     * @return 标准化后的 PDF 文本内容
     */
    public String extractTextFromLocalPath(String path) {
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            return normalizeExtractedText(TIKA.parseToString(inputStream));
        } catch (Exception e) {
            throw new RuntimeException("PDF解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 抽取后的纯文本规范化：换行统一为 {@code \n}，连续空格/Tab 合并，连续空行压成一段。
     * PDF 抽取与简历 {@code rawText} 导入共用，避免两套空白规则。
     */
    public static String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
