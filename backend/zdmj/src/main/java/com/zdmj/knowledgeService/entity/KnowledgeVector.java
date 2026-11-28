package com.zdmj.knowledgeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 文档分块向量实体类
 * 对应数据库表：knowledge_vectors
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_vectors", autoResultMap = true)
public class KnowledgeVector extends BaseEntity {
    /**
     * 文档分块向量ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档编号，对应 document_id，必填；归属及可见范围由文档确定
     */
    private Long documentId;

    /**
     * 向量
     */
    private String embedding;

    /**
     * 内容
     */
    private String content;

    /**
     * 元数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 文档内分块编号，对应 chunk_index，必填且非负，同一文档内唯一
     */
    private Integer chunkIndex;

    /**
     * 文档块哈希
     */
    private String chunkHash;

    /**
     * 文档块Token数量
     */
    private Integer tokenCount;
}
