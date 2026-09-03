package com.zdmj.common.ai;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 Spring AI 1.1 {@link BeanOutputConverter} 是否已覆盖 ChatUtil.stripCodeFence 的职责。
 */
class BeanOutputConverterFenceTest {

    private final BeanOutputConverter<SampleOut> converter = new BeanOutputConverter<>(SampleOut.class);

    @Test
    void convert_whenMultilineJsonFence_shouldParse() {
        SampleOut parsed = converter.convert("""
                ```json
                {"name":"Ada","score":9}
                ```
                """);

        assertEquals("Ada", parsed.getName());
        assertEquals(9, parsed.getScore());
    }

    @Test
    void convert_whenMultilineBareFence_shouldParse() {
        SampleOut parsed = converter.convert("""
                ```
                {"name":"Bob","score":3}
                ```
                """);

        assertEquals("Bob", parsed.getName());
        assertEquals(3, parsed.getScore());
    }

    @Test
    void convert_whenRawJson_shouldParse() {
        SampleOut parsed = converter.convert("{\"name\":\"Cara\",\"score\":1}");

        assertEquals("Cara", parsed.getName());
        assertEquals(1, parsed.getScore());
    }

    @Test
    void convert_whenSingleLineJsonFence_shouldFailBecauseCleanerDropsContent() {
        assertThrows(RuntimeException.class,
                () -> converter.convert("```json {\"name\":\"Dee\",\"score\":4} ```"));
    }

    @Test
    void convert_whenOpeningFenceWithoutClose_shouldFail() {
        assertThrows(RuntimeException.class,
                () -> converter.convert("```json\n{\"name\":\"Eve\",\"score\":2}"));
    }

    @Data
    static class SampleOut {
        private String name;
        private int score;
    }
}
