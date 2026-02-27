package com.zdmj.knowledgeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbListTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 知识库实体类
 * 对应数据库表：knowledge_bases
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_bases")
public class KnowledgeBases extends BaseEntity {

    /**
     * 知识库ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 文件类型
     */
    private Integer fileType;

    /**
     * 知识标签（JSONB数组）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<String> tag;

    /**
     * 知识类型
     */
    private Integer type;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 关联的向量ID数组（JSONB数组）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<Long> vectorIds;

    /**
     * 最近一次向量化任务ID
     */
    private String vectorTaskId;

    /**
     * 最近一次任务状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）
     */
    private String vectorTaskStatus;
}
