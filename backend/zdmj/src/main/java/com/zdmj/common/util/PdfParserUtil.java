package com.zdmj.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 文件文本提取与规范化工具。
 *
 * <p>
 * 调用方负责验证文件来源、资源归属及容量，并关闭输入流。
 * 提取过程中超过指定长度时立即失败。
 * </p>
 */
@Component
public class PdfParserUtil {

    private static final Tika TIKA = new Tika();

    /** 简历与能力画像请求文件的最大提取长度，按 UTF-16 代码单元计数。 */
    private static final int REQUEST_FILE_MAX_EXTRACTED_CHARS = 100_000;

    /**
     * 提取简历或能力画像请求文件中的完整文本。
     *
     * @param input 已完成来源校验的请求文件输入流，由调用方关闭
     * @return 规范化后的完整文本
     * @throws BusinessException 文本超过限制、文件无法解析或请求文件读取失败
     */
    public String extractText(InputStream input) {
        try {
            return extractText(input, REQUEST_FILE_MAX_EXTRACTED_CHARS);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, "请求文件读取失败", exception);
        }
    }

    /**
     * 按指定容量提取完整文本。
     *
     * 长度限制作用于规范化之前的解析输出，按照 UTF-16 代码单元计数。
     * 输入流读取异常交由调用方按文件来源转换。
     *
     * @param input             已完成来源及归属校验的输入流，由调用方关闭
     * @param maxExtractedChars 最大提取长度，必须为正数
     * @return 规范化后的完整文本
     * @throws IOException       输入流读取失败
     * @throws BusinessException 文本超过限制或文件无法解析
     */
    public String extractText(InputStream input, int maxExtractedChars) throws IOException {
        Objects.requireNonNull(input, "输入流不能为空");

        if (maxExtractedChars <= 0) {
            throw new IllegalArgumentException("最大文本提取数量必须为正数");
        }

        // 1.获取解析器
        Parser parser = TIKA.getParser();
        BodyContentHandler handler = new BodyContentHandler(maxExtractedChars);

        // 2.创建解析上下文
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        // 3.解析文档
        try {
            parser.parse(input, handler, new Metadata(), context);
        } catch (SAXException | TikaException exception) {
            if (WriteLimitReachedException.isWriteLimitReached(exception)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档提取文本超过允许长度：" + maxExtractedChars, exception);
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档提取文本失败", exception);
        }

        // 4.返回规范化后的文本
        return normalizeExtractedText(handler.toString());
    }

    /**
     * 规范化提取文本。
     *
     * 统一换行符，合并连续空格和制表符，将连续空行压缩为一个空行。
     * 文件解析与简历纯文本导入共用此规则。
     *
     * @param text 原始文本，允许为空
     * @return 规范化文本，空输入返回空字符串
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
