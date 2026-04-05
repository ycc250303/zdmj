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
 * 知识库向量实体类
 * 对应数据库表：knowledge_vectors
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_vectors", autoResultMap = true)
public class KnowledgeVector extends BaseEntity {
    /**
     * 知识库向量ID
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
     * 文档块索引
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
