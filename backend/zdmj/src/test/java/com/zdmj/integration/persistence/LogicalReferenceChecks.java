package com.zdmj.integration.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;

/**
 * 逻辑外键一致性检查。直接插入的孤立记录会进入结果，合法引用不会进入。
 */
public final class LogicalReferenceChecks {

    private LogicalReferenceChecks() {
    }

    public record Orphan(String sourceTable, long sourceId, String column, String targetTable) {
    }

    public static List<Orphan> find(JdbcTemplate jdbcTemplate) {
        List<Orphan> orphans = new ArrayList<>();
        orphans.addAll(query(jdbcTemplate, """
                SELECT m.id FROM messages m
                LEFT JOIN conversations c ON c.id = m.conversation_id
                WHERE c.id IS NULL
                ORDER BY m.id
                """, "messages", "conversation_id", "conversations"));
        orphans.addAll(query(jdbcTemplate, """
                SELECT d.id FROM knowledge_documents d
                LEFT JOIN users u ON u.id = d.user_id
                WHERE d.scope = ? AND u.id IS NULL
                ORDER BY d.id
                """, "knowledge_documents", "user_id", "users", KnowledgeScopeEnum.USER.getCode()));
        orphans.addAll(query(jdbcTemplate, """
                SELECT v.id FROM knowledge_vectors v
                LEFT JOIN knowledge_documents d ON d.id = v.document_id
                WHERE d.id IS NULL ORDER BY v.id
                """, "knowledge_vectors", "document_id", "knowledge_documents"));
        orphans.addAll(query(jdbcTemplate, """
                SELECT j.id FROM jobs j
                LEFT JOIN companies c ON c.id = j.company_id
                WHERE c.id IS NULL
                ORDER BY j.id
                """, "jobs", "company_id", "companies"));
        return orphans;
    }

    private static List<Orphan> query(JdbcTemplate jdbcTemplate, String sql, String sourceTable, String column,
            String targetTable, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Orphan(sourceTable, rs.getLong("id"), column, targetTable), args);
    }
}
