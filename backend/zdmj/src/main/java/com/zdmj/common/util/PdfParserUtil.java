package com.zdmj.common.util;

import java.io.InputStream;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

@Component
public class PdfParserUtil {
    private static final Tika TIKA = new Tika();

    /**
     * 从可信输入流提取并规范化文本，调用方负责关闭输入流。
     * @param input 已完成来源及归属校验的输入流
     * @return 规范化文本
     * @throws BusinessException 文件内容无法读取或解析
     */
    public String extractText(InputStream input) {
        java.util.Objects.requireNonNull(input, "input 不能为空");
        try {
            return normalizeExtractedText(TIKA.parseToString(input));
        } catch (java.io.IOException | org.apache.tika.exception.TikaException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件文本提取失败", exception);
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
