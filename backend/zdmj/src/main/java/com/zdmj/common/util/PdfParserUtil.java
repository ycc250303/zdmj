package com.zdmj.common.util;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.zdmj.common.storage.FileUploadService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PdfParserUtil {
    private static final Tika TIKA = new Tika();

    private final FileUploadService fileUploadService;

    public String extractTextFromUrl(String url) {
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

    private InputStream openPdfInputStream(String url) throws Exception {
        if (fileUploadService.isManagedCosUrl(url)) {
            return fileUploadService.openInputStreamFromUrl(url);
        }
        return URI.create(url).toURL().openStream();
    }

    public String extractTextFromLocalPath(String path) {
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
