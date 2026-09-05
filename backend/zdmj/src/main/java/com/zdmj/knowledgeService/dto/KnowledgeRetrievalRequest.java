package com.zdmj.knowledgeService.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识库排序检索请求（不含整篇展开、不生成回答）。
 */
@Data
public class KnowledgeRetrievalRequest {

    /**
     * 用户原始问句
     */
    @NotBlank(message = "查询内容不能为空")
    private String query;

    /**
     * 用户库文档范围：{@code null} 检索用户库全部；空列表不检索用户库。
     */
    private List<Long> ragDocumentIds;

    /**
     * 是否检索系统知识库
     */
    private boolean useSystemKnowledge;
}
