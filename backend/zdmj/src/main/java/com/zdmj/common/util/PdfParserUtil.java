package com.zdmj.common.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.Tika;

public class PdfParserUtil {
    private static final Tika TIKA = new Tika();

    private PdfParserUtil() {
    }

    public static String extractTextFromCosKey(String key) {
        try (InputStream inputStream = CosUtil.getObjectInputStream(key)) {
            return normalize(TIKA.parseToString(inputStream));
        } catch (Exception e) {
            throw new RuntimeException("PDF解析失败：" + e.getMessage(), e);
        }
    }

    public static String extractTextFromLocalPath(String path) {
        try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
            return normalize(TIKA.parseToString(inputStream));
        } catch (Exception e) {
            throw new RuntimeException("PDF解析失败：" + e.getMessage(), e);
        }
    }

    private static String normalize(String text) {
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
