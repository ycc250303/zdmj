package com.zdmj.common.util;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfParserUtilTest {

    private FileUploadService fileUploadService;
    private PdfParserUtil pdfParserUtil;

    @BeforeEach
    void setUp() {
        fileUploadService = Mockito.mock(FileUploadService.class);
        when(fileUploadService.isManagedCosUrl(anyString())).thenReturn(false);
        pdfParserUtil = new PdfParserUtil(fileUploadService);
        UserHolder.set(UserContext.of(1L, "tester"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
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
    void extractTextFromUrl_shouldRejectNonCosUrl() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfParserUtil.extractTextFromUrl("https://invalid-host-for-test-zdmj/pdf.pdf"));
        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(fileUploadService, never()).openInputStreamFromUrl(anyString(), Mockito.anyLong());
    }

    @Test
    void extractTextFromUrl_shouldRejectFileUrl(@TempDir Path tempDir) throws Exception {
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");
        Path copy = tempDir.resolve("sample.pdf");
        Files.copy(Path.of(resource.toURI()), copy);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfParserUtil.extractTextFromUrl(copy.toUri().toString()));
        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(fileUploadService, never()).openInputStreamFromUrl(anyString(), Mockito.anyLong());
    }

    @Test
    void extractTextFromUrl_managedCos_shouldParseOwnedObject() throws Exception {
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");
        String cosUrl = "https://bucket.cos.ap-shanghai.myqcloud.com/user-1/resume/a.pdf";
        when(fileUploadService.isManagedCosUrl(cosUrl)).thenReturn(true);
        when(fileUploadService.openInputStreamFromUrl(eq(cosUrl), eq(1L)))
                .thenAnswer(invocation -> Files.newInputStream(Path.of(resource.toURI())));

        String parsedText = pdfParserUtil.extractTextFromUrl(cosUrl);

        assertTrue(parsedText.contains("ZDMJ"));
        verify(fileUploadService).openInputStreamFromUrl(cosUrl, 1L);
    }

    @Test
    void extractTextFromUrl_withOwnerId_shouldNotRequireThreadLocal() throws Exception {
        UserHolder.clear();
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");
        String cosUrl = "https://bucket.cos.ap-shanghai.myqcloud.com/user-9/knowledge/a.pdf";
        when(fileUploadService.isManagedCosUrl(cosUrl)).thenReturn(true);
        when(fileUploadService.openInputStreamFromUrl(eq(cosUrl), eq(9L)))
                .thenAnswer(invocation -> Files.newInputStream(Path.of(resource.toURI())));

        String parsedText = pdfParserUtil.extractTextFromUrl(cosUrl, 9L);

        assertTrue(parsedText.contains("ZDMJ"));
    }

    @Test
    void extractTextFromUrl_blank_shouldThrowValidation() {
        BusinessException ex = assertThrows(BusinessException.class, () -> pdfParserUtil.extractTextFromUrl("  "));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }
}
