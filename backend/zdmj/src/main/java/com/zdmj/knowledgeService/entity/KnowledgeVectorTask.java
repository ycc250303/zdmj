package com.zdmj.knowledgeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_vector_tasks")
public class KnowledgeVectorTask extends BaseEntity {
    /**
     * 任务ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 任务类型
     */
    private Integer taskType;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
