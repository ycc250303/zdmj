package com.zdmj.knowledgeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识文档实体
 * 对应表：knowledge_documents
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_documents", autoResultMap = true)
public class KnowledgeDocument extends BaseEntity {
    /**
     * 知识库ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 来源类型
     */
    private Integer type;

    /**
     * 来源地址
     */
    private String content;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容哈希
     */
    private String contentHash;

    /**
     * 文档向量化状态
     */
    private Integer embeddingStatus;

    /**
     * 文档分块数量
     */
    private Integer chunkCount;

    /**
     * 最近一次向量化完成时间
     */
    private LocalDateTime lastEmbeddedAt;

    /**
     * 最近一次向量化错误信息
     */
    private String lastError;

    /**
     * 文档元数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}
