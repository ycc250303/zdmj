package com.zdmj.conversationService.support;

import com.zdmj.conversationService.entity.Conversation;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话 config 解析。
 */
public final class ConversationContextSupport {

    public static final String CONFIG_USE_SYSTEM_KNOWLEDGE = "useSystemKnowledge";
    public static final String CONFIG_RAG_DOCUMENT_IDS = "ragDocumentIds";

    private ConversationContextSupport() {
    }

    /**
     * 收口 config 键：只保留 useSystemKnowledge、ragDocumentIds，其余键丢弃。
     * 首条消息发出前可反复写入；ragDocumentIds 缺省不写入（检索时视为用户库全部文档）。
     */
    public static Map<String, Object> freezeConfig(Map<String, Object> incoming) {
        Map<String, Object> config = new LinkedHashMap<>();
        Object rawFlag = incoming == null ? null : incoming.get(CONFIG_USE_SYSTEM_KNOWLEDGE);
        config.put(CONFIG_USE_SYSTEM_KNOWLEDGE, parseBooleanFlag(rawFlag));
        if (incoming != null && incoming.containsKey(CONFIG_RAG_DOCUMENT_IDS)) {
            List<Long> ids = parseRagDocumentIds(incoming.get(CONFIG_RAG_DOCUMENT_IDS));
            if (ids != null) {
                config.put(CONFIG_RAG_DOCUMENT_IDS, ids);
            }
        }
        return config;
    }

    public static boolean resolveUseSystemKnowledge(Conversation conversation) {
        if (conversation == null || conversation.getConfig() == null) {
            return false;
        }
        return parseBooleanFlag(conversation.getConfig().get(CONFIG_USE_SYSTEM_KNOWLEDGE));
    }

    /**
     * null 表示未限定文档（检索用户库全部）；空列表表示不检索用户文档。
     */
    public static List<Long> resolveRagDocumentIds(Conversation conversation) {
        if (conversation == null || conversation.getConfig() == null) {
            return null;
        }
        return parseRagDocumentIds(conversation.getConfig().get(CONFIG_RAG_DOCUMENT_IDS));
    }

    public static List<Long> parseRagDocumentIds(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<Long> ids = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    private static boolean parseBooleanFlag(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return false;
    }
}
