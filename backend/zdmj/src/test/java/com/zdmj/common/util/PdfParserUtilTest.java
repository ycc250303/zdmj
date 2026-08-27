package com.zdmj.common.util;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import com.zdmj.common.storage.FileUploadService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PdfParserUtilTest {

    private FileUploadService fileUploadService;
    private PdfParserUtil pdfParserUtil;

    @BeforeEach
    void setUp() {
        fileUploadService = Mockito.mock(FileUploadService.class);
        when(fileUploadService.isManagedCosUrl(anyString())).thenReturn(false);
        pdfParserUtil = new PdfParserUtil(fileUploadService);
    }

    @Test
    void extractTextFromLocalPath_shouldParseClasspathFixture() throws Exception {
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");

        String parsedText = pdfParserUtil.extractTextFromLocalPath(Path.of(resource.toURI()).toString());

        assertTrue(parsedText != null && !parsedText.isBlank(), "本地 PDF 解析结果不应为空");
        assertTrue(parsedText.contains("ZDMJ"), "应解析出 fixture 中的文本");
    }

    @Test
    void extractTextFromLocalPath_shouldWrapExceptionForMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("not-exists.pdf");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pdfParserUtil.extractTextFromLocalPath(missing.toString()));
        assertTrue(ex.getMessage().startsWith("PDF解析失败："));
    }

    @Test
    void extractTextFromUrl_shouldWrapExceptionForInvalidHost() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pdfParserUtil.extractTextFromUrl("https://invalid-host-for-test-zdmj/pdf.pdf"));
        assertTrue(ex.getMessage().startsWith("PDF解析失败："));
    }

    @Test
    void extractTextFromUrl_shouldParseFileUrl(@TempDir Path tempDir) throws Exception {
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");

        Path copy = tempDir.resolve("sample.pdf");
        Files.copy(Path.of(resource.toURI()), copy);

        String parsedText = pdfParserUtil.extractTextFromUrl(copy.toUri().toString());

        assertTrue(parsedText != null && !parsedText.isBlank(), "file:// PDF 解析结果不应为空");
    }
}
