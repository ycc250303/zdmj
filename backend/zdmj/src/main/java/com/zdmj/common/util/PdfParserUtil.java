package com.zdmj.common.util;

import com.zdmj.common.context.CurrentActor;
import java.io.InputStream;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

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
     * 解析操作者拥有的 COS 对象，身份由调用方显式传入。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param url 本系统上传文件的 HTTPS 地址
     * @return 规范化后的文件文本
     * @throws BusinessException 地址无效、对象不属于操作者或文本提取失败
     */
    public String extractTextFromUrl(CurrentActor actor, String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件地址不能为空");
        }
        java.util.Objects.requireNonNull(actor, "actor 不能为空");
        String trimmed = url.trim();
        if (!fileUploadService.isManagedCosUrl(trimmed)) {
            throw new BusinessException(ErrorCode.URL_FORMAT_ERROR, "仅支持本系统已上传的文件");
        }
        try (InputStream inputStream = fileUploadService.openInputStreamFromUrl(actor, trimmed)) {
            return normalizeExtractedText(TIKA.parseToString(inputStream));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF解析失败：" + e.getMessage(), e);
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
