package com.zdmj.jobService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbStringTypeHandler;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位关联图谱实体类。
 * 对应数据库表：{@code job_career_graphs}。
 *
 * <p>图谱由两部分构成：</p>
 * <ol>
 *     <li>{@link #verticalPath} — 垂直晋升路径（JSONB 数组，≥3 个节点）。</li>
 *     <li>{@link #transitionPaths} — 换岗路径（JSONB 数组，≥5 条，每条 ≥2 节点）。</li>
 * </ol>
 *
 * <p>JSON 结构的序列化/反序列化在 Service 层通过 Jackson 完成；DB 列为 JSONB，
 * 实体字段统一使用 {@link JsonbStringTypeHandler} 保持"原始 JSON 文本"形式，
 * 与 {@link JobCapabilityProfile} 的做法保持一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "job_career_graphs", autoResultMap = true)
public class JobCareerGraph extends BaseEntity {

    /** 图谱ID（主键，自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位ID（逻辑外键：{@code jobs.id}） */
    private Long jobId;

    /** 岗位识别置信度（0~1），与岗位能力画像保持一致 */
    private BigDecimal roleConfidence;

    /** 实际使用的提示词名称，如 {@code job-career-graph/java-backend} */
    private String promptName;

    /** 岗位类型展示值，如 {@code java-backend}/{@code frontend}/{@code default} */
    private String targetRoleType;

    /** 当前岗位节点 JSON：{@code { level, title, roleType, description }} */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String currentNode;

    /** 垂直晋升路径 JSON 数组（VerticalPathNode[]） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String verticalPath;

    /** 换岗路径 JSON 数组（TransitionPath[]） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String transitionPaths;

    /** 一句话总结 */
    private String summary;
}
