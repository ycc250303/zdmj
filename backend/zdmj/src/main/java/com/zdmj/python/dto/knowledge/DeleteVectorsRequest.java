package com.zdmj.python.dto.knowledge;

import lombok.Data;

/**
 * 整库向量删除请求体
 * 对应 Python 接口：POST /ai/knowledge/vectors/delete
 *
 * 公共字段收敛在父类 {@link KnowledgeBaseUserRequest} 中。
 */
@Data
public class DeleteVectorsRequest extends KnowledgeBaseUserRequest {
}
