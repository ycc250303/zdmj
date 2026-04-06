package com.zdmj.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfParserUtilTest {
    private static final String TEST_URL =
            "https://zdmj-1381832847.cos.ap-shanghai.myqcloud.com/user-1/resume/%E5%B0%B9%E8%AF%9A%E6%88%90-%E7%AE%80%E5%8E%86-8dac604635c24287b0aee44739cb6456.pdf";

    @Test
    void extractTextFromUrl_shouldParsePdfText() {
        String parsedText = PdfParserUtil.extractTextFromUrl(TEST_URL);
        System.out.println(parsedText);
        assertTrue(parsedText != null && !parsedText.isBlank(), "URL PDF解析结果不应为空");
    }

    @Test
    void extractTextFromUrl_shouldWrapException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> PdfParserUtil.extractTextFromUrl("https://invalid-host-for-test-zdmj/pdf.pdf"));
        assertTrue(ex.getMessage().startsWith("PDF解析失败："));
    }

    @Test
    void extractTextFromUrl_realCosFile_shouldParseText() {
        String text = PdfParserUtil.extractTextFromUrl(TEST_URL);
        System.out.println(text);
        assertTrue(text != null && !text.isBlank(), "COS PDF解析结果不应为空");
    }
}
