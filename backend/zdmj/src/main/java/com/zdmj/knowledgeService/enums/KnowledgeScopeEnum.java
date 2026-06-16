package com.zdmj.knowledgeService.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 知识库 scope 约定（与 knowledge_bases.scope 一致）。
 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeScopeEnum {

    /** 用户私有库 */
    USER(1),
    /** 系统通用库 */
    SYSTEM(2),
    /** 学习路线库（离线导入，报告 RAG 可读） */
    LEARNING_PATH(3);

    private final int code;

    /** 系统级知识库向量行的 user_id（与 sql/import_system_knowledge.py 一致） */
    public static final long SYSTEM_OWNER_USER_ID = 0L;
}
