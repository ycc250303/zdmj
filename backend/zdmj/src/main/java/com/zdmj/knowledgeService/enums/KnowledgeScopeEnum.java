package com.zdmj.knowledgeService.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 知识文档使用范围，对应 knowledge_documents.scope；统一维护数据库码值。
 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeScopeEnum {

    /** 用户私有资料，码值1；文档用户编号为所属用户。 */
    USER(1),
    /** 系统通用资料，码值2；文档用户编号为空，会话检索开关控制可见性。 */
    SYSTEM(2),
    /** 学习路径资料，码值3；文档用户编号为空，供职业报告检索。 */
    LEARNING_PATH(3);

    /** 持久化码值，对应 scope 列，业务条件通过枚举取得。 */
    private final int code;
}
