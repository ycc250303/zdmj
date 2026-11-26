package com.zdmj.common.util;

import static org.assertj.core.api.Assertions.*;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfParserUtilTest {
    private final PdfParserUtil parser = new PdfParserUtil();

    @Test
    void extractText_shouldParsePdf() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/minimal-sample.pdf")) {
            assertThat(parser.extractText(input)).contains("ZDMJ");
        }
    }

    @Test
    void extractText_readFailure_shouldRetainCause() {
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }
        };
        assertThatThrownBy(() -> parser.extractText(input)).isInstanceOf(BusinessException.class)
                .hasCauseInstanceOf(IOException.class).extracting("code").isEqualTo(ErrorCode.FILE_READ_FAILED.getCode());
        assertThatThrownBy(() -> parser.extractText(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 超过十万字符的文档完整提取并保留末尾() throws Exception {
        String text = "文档开始\n" + "知识内容包含岗位要求和项目经验。\n".repeat(8_000) + "DOCUMENT_END_MARKER";
        try (InputStream input = textInput(text)) {
            String extracted = parser.extractText(input, 1_000_000);
            assertThat(extracted).hasSizeGreaterThan(100_000).isEqualTo(text);
        }
    }

    @Test
    void 超过指定容量明确失败且保留超限原因() {
        assertThatThrownBy(() -> {
            try (InputStream input = textInput("A".repeat(100_001))) {
                parser.extractText(input, 100_000);
            }
        }).isInstanceOf(BusinessException.class).hasMessageContaining("100000")
                .satisfies(exception -> {
                    assertThat(((BusinessException) exception).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(org.apache.tika.exception.WriteLimitReachedException.isWriteLimitReached(exception)).isTrue();
                });
    }

    @Test
    void 请求文件入口超过十万字符明确失败() {
        assertThatThrownBy(() -> {
            try (InputStream input = textInput("A".repeat(100_001))) {
                parser.extractText(input);
            }
        }).isInstanceOf(BusinessException.class).hasMessageContaining("100000");
    }

    @Test
    void 非正数容量属于调用缺陷() {
        assertThatThrownBy(() -> parser.extractText(InputStream.nullInputStream(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.extractText(InputStream.nullInputStream(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InputStream textInput(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void normalizeExtractedText_shouldCollapseWhitespace() {
        assertThat(PdfParserUtil.normalizeExtractedText("Hello   world\r\n\n\n\nNext")).isEqualTo("Hello world\n\nNext");
        assertThat(PdfParserUtil.normalizeExtractedText(null)).isEmpty();
        assertThat(PdfParserUtil.normalizeExtractedText(PdfParserUtil.normalizeExtractedText("same"))).isEqualTo("same");
    }
}
