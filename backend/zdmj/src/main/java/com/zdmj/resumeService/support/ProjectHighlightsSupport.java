package com.zdmj.resumeService.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目亮点（project_experiences.highlights JSONB）读写归一化。
 */
public final class ProjectHighlightsSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private ProjectHighlightsSupport() {
    }

    /**
     * 将单条文本或 JSON 数组字符串归一化为可写入 JSONB 的 JSON 数组字符串。
     */
    public static String normalizeForStorage(String highlights) {
        if (!StringUtils.hasText(highlights)) {
            return null;
        }
        String trimmed = highlights.trim();
        if (isJsonArray(trimmed)) {
            return trimmed;
        }
        try {
            return MAPPER.writeValueAsString(List.of(trimmed));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 List 归一化为 JSON 数组字符串。
     */
    public static String normalizeForStorage(List<?> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            return null;
        }
        List<String> values = highlights.stream()
                .filter(v -> v != null && StringUtils.hasText(String.valueOf(v)))
                .map(String::valueOf)
                .collect(Collectors.toList());
        if (values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取亮点纯文本，便于规则匹配（如从亮点中补充奖项）。
     */
    public static String toPlainText(String highlights) {
        if (!StringUtils.hasText(highlights)) {
            return null;
        }
        String trimmed = highlights.trim();
        if (!isJsonArray(trimmed)) {
            return trimmed;
        }
        try {
            List<String> values = MAPPER.readValue(trimmed, STRING_LIST);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return String.join("\n", values);
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static boolean isJsonArray(String value) {
        return value.startsWith("[") && value.endsWith("]");
    }
}
