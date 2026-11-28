package com.zdmj.knowledgeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.knowledgeService.enums.KnowledgeFileStatusEnum;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
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
     * 文档主键，对应 id，由数据库生成。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档使用范围，对应 scope；码值由 {@link KnowledgeScopeEnum} 维护，创建时明确赋值。 */
    private Integer scope;

    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 来源类型，对应 type：1上传PDF/Markdown，2仓库链接，3外部文档。
     */
    private Integer type;

    /**
     * 文件类型为 user-{id}/{uuid}.pdf 或 .md 相对标识；链接类型为来源 URL。
     */
    private String content;

    /** 原文件状态，对应 file_status，码值由 {@link KnowledgeFileStatusEnum} 维护。 */
    private Integer fileStatus;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 私有原文件或系统清理正文的 SHA-256，对应 content_hash，为64位十六进制摘要。
     */
    private String contentHash;

    /**
     * 文档向量化状态，码值见 {@link com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum}
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
     * 非空文档元数据，对应 metadata；包含下载信息及替换期间的待处理标识。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}
