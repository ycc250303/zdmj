package com.zdmj.common.util;

import com.zdmj.common.context.CurrentActor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.storage.FileUploadService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfParserUtilTest {

    private final CurrentActor actor = new CurrentActor(1L);

    private FileUploadService fileUploadService;
    private PdfParserUtil pdfParserUtil;

    @BeforeEach
    void setUp() {
        fileUploadService = Mockito.mock(FileUploadService.class);
        when(fileUploadService.isManagedCosUrl(anyString())).thenReturn(false);
        pdfParserUtil = new PdfParserUtil(fileUploadService);
    }

    @Test
    void extractTextFromUrl_shouldRejectNonCosUrl() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfParserUtil.extractTextFromUrl(actor, "https://invalid-host-for-test-zdmj/pdf.pdf"));
        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(fileUploadService, never()).openInputStreamFromUrl(any(CurrentActor.class), anyString());
    }

    @Test
    void extractTextFromUrl_shouldRejectFileUrl(@TempDir Path tempDir) throws Exception {
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");
        Path copy = tempDir.resolve("sample.pdf");
        Files.copy(Path.of(resource.toURI()), copy);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfParserUtil.extractTextFromUrl(actor, copy.toUri().toString()));
        assertEquals(ErrorCode.URL_FORMAT_ERROR.getCode(), ex.getCode());
        verify(fileUploadService, never()).openInputStreamFromUrl(any(CurrentActor.class), anyString());
    }

    @Test
    void extractTextFromUrl_managedCos_shouldParseOwnedObject() throws Exception {
        URL resource = PdfParserUtilTest.class.getResource("/fixtures/minimal-sample.pdf");
        assertTrue(resource != null, "测试 PDF 资源缺失");
        String cosUrl = "https://bucket.cos.ap-shanghai.myqcloud.com/user-1/resume/a.pdf";
        when(fileUploadService.isManagedCosUrl(cosUrl)).thenReturn(true);
        when(fileUploadService.openInputStreamFromUrl(org.mockito.ArgumentMatchers.same(actor), eq(cosUrl)))
                .thenAnswer(invocation -> Files.newInputStream(Path.of(resource.toURI())));

        String parsedText = pdfParserUtil.extractTextFromUrl(actor, cosUrl);

        assertTrue(parsedText.contains("ZDMJ"));
        verify(fileUploadService).openInputStreamFromUrl(actor, cosUrl);
    }

    @Test
    void extractTextFromUrl_blank_shouldThrowValidation() {
        BusinessException ex = assertThrows(BusinessException.class, () -> pdfParserUtil.extractTextFromUrl(actor, "  "));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    void normalizeExtractedText_shouldCollapseHorizontalAndVerticalWhitespace() {
        String raw = "Hello   world\r\n\n\n\nNext";
        assertEquals("Hello world\n\nNext", PdfParserUtil.normalizeExtractedText(raw));
        assertEquals("", PdfParserUtil.normalizeExtractedText(null));
        assertEquals("same", PdfParserUtil.normalizeExtractedText(PdfParserUtil.normalizeExtractedText("same")));
    }
}
