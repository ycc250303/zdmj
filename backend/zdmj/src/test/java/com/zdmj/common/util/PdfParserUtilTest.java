package com.zdmj.common.util;

import static org.assertj.core.api.Assertions.*;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import java.io.InputStream;
import java.io.IOException;
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
                .hasCauseInstanceOf(IOException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThatThrownBy(() -> parser.extractText(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizeExtractedText_shouldCollapseWhitespace() {
        assertThat(PdfParserUtil.normalizeExtractedText("Hello   world\r\n\n\n\nNext")).isEqualTo("Hello world\n\nNext");
        assertThat(PdfParserUtil.normalizeExtractedText(null)).isEmpty();
        assertThat(PdfParserUtil.normalizeExtractedText(PdfParserUtil.normalizeExtractedText("same"))).isEqualTo("same");
    }
}
