package com.zdmj.common.util;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.Tika;

public class PdfParserUtil {
    private static final Tika TIKA = new Tika();

    private PdfParserUtil() {
    }

    public static String extractTextFromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new RuntimeException("PDF解析失败：文件地址不能为空");
        }
        try {
            try (InputStream inputStream = openPdfInputStream(url.trim())) {
                return normalize(TIKA.parseToString(inputStream));
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF解析失败：" + e.getMessage(), e);
        }
    }

    private static InputStream openPdfInputStream(String url) throws Exception {
        if (CosUtil.isManagedCosUrl(url)) {
            return CosUtil.openInputStreamFromUrl(url);
        }
        return URI.create(url).toURL().openStream();
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
