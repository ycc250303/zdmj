package com.zdmj.python.dto.knowledge;

import com.zdmj.python.dto.PythonApiRequest;
import lombok.Data;

/**
 * 知识库 + 用户通用请求体
 * <p>
 * 包含 Python 知识库相关接口通用的两个字段：
 * - knowledgeId：知识库ID（knowledge_bases.id）
 * - userId：用户ID（用于数据隔离）
 * <p>
 * 具体业务请求（如向量化、整库删除）可在此基础上扩展。
 */
@Data
public class KnowledgeBaseUserRequest extends PythonApiRequest {

    /**
     * 知识库ID（knowledge_bases.id）
     */
    private Long knowledgeId;

    /**
     * 用户ID（用于数据隔离）
     */
    private Long userId;
}
