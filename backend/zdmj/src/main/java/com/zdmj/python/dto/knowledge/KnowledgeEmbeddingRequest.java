package com.zdmj.python.dto.knowledge;

import lombok.Data;

/**
 * 知识库向量化请求体（创建 / 重跑）
 * 对应 Python 接口：POST /ai/knowledge/embedding
 *
 * 目前字段与整库删除请求相同，统一收敛到父类 {@link KnowledgeBaseUserRequest} 中。
 */
@Data
public class KnowledgeEmbeddingRequest extends KnowledgeBaseUserRequest {
}
