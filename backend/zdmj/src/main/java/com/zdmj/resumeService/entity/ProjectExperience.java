package com.zdmj.resumeService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/**
 * 项目经历实体类
 * 对应数据库表：project_experiences
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_experiences")
public class ProjectExperience extends BaseEntity {
    /**
     * 项目经历ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目开始时间
     */
    private LocalDate startDate;

    /**
     * 项目结束时间（进行中可为空）
     */
    private LocalDate endDate;

    /**
     * 在项目中的角色和职责
     */
    private String role;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 技术栈（JSONB数组，如["React", "TypeScript", "Node.js"]）
     */
    private String techStack;

    /**
     * 项目亮点（JSONB数组，包含技术难点、成果等）
     */
    private String highlights;

    /**
     * 项目链接
     */
    private String url;

    /**
     * 是否在简历中展示
     */
    private Boolean visible;

    /**
     * 项目分析状态（枚举：1=committed已提交/2=mining挖掘中/3=polishing打磨中/4=completed已完成）
     * 说明：用于跟踪AI分析流程，不影响简历展示
     */
    private Integer status;

    /**
     * AI分析结果（JSONB格式，包含问题、解决方案、评分）
     * 示例：
     * {
     *   "problem": [
     *     {
     *       "type": "问题类型",
     *       "content": "问题描述"
     *     }
     *   ],
     *   "solution": [
     *     {
     *       "type": "解决方案类型",
     *       "content": "解决方案描述"
     *     }
     *   ],
     *   "score": 85
     * }
     */
    private String lookupResult;
}
