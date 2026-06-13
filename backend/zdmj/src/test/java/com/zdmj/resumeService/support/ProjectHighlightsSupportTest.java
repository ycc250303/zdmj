package com.zdmj.resumeService.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectHighlightsSupportTest {

    @Test
    void normalizeForStorage_plainString_shouldWrapAsJsonArray() {
        String out = ProjectHighlightsSupport.normalizeForStorage("该项目获全国一等奖");
        assertEquals("[\"该项目获全国一等奖\"]", out);
    }

    @Test
    void normalizeForStorage_jsonArray_shouldKeepAsIs() {
        String input = "[\"亮点A\",\"亮点B\"]";
        assertEquals(input, ProjectHighlightsSupport.normalizeForStorage(input));
    }

    @Test
    void normalizeForStorage_list_shouldSerialize() {
        String out = ProjectHighlightsSupport.normalizeForStorage(List.of("a", "b"));
        assertEquals("[\"a\",\"b\"]", out);
    }

    @Test
    void normalizeForStorage_blank_shouldReturnNull() {
        assertNull(ProjectHighlightsSupport.normalizeForStorage("  "));
    }

    @Test
    void toPlainText_jsonArray_shouldJoinLines() {
        String plain = ProjectHighlightsSupport.toPlainText("[\"该项目获全国一等奖\"]");
        assertEquals("该项目获全国一等奖", plain);
    }

    @Test
    void toPlainText_plainString_shouldReturnSelf() {
        assertEquals("plain", ProjectHighlightsSupport.toPlainText("plain"));
    }
}
