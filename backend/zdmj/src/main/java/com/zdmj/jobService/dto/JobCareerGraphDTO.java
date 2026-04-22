package com.zdmj.jobService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/**
 * 岗位关联图谱返回体。
 *
 * <p>包含两大部分：</p>
 * <ol>
 *     <li>{@link #verticalPath} — 垂直岗位图谱（岗位未来发展路径，≥3 个节点）。</li>
 *     <li>{@link #transitionPaths} — 换岗路径图谱（≥5 条路径，每条 ≥2 个节点）。</li>
 * </ol>
 *
 * <p>同时返回 {@link #currentNode} 标注"当前所处岗位"节点，便于前端在图谱中高亮起点。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobCareerGraphDTO {

    /** 岗位ID */
    private Long jobId;

    /** 岗位类型展示值（如 {@code java-backend}） */
    private String targetRoleType;

    /** 当前岗位所在节点 */
    private CurrentNode currentNode;

    /** 垂直晋升路径（按 level 升序） */
    private List<VerticalPathNode> verticalPath;

    /** 换岗路径图谱（≥5 条） */
    private List<TransitionPath> transitionPaths;

    /** 一句话总结 */
    private String summary;

    /** 当前岗位节点元信息 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentNode {
        /** 当前层级（与 {@link VerticalPathNode#level} 对应） */
        private Integer level;
        /** 当前岗位名称 */
        private String title;
        /** 归一化角色码，如 {@code java_backend} */
        private String roleType;
        /** 简要描述 */
        private String description;
    }

    /** 垂直晋升路径节点 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VerticalPathNode {
        /** 层级序号（从 1 开始，升序递增） */
        private Integer level;
        /** 岗位名称（如"高级 Java 工程师"） */
        private String title;
        /** 岗位描述 */
        private String description;
        /** 核心职责 */
        private List<String> responsibilities;
        /** 关键能力/技能要求 */
        private List<String> keyRequirements;
        /** 典型年限区间（如 {@code "3-5"}） */
        private String typicalYears;
        /** 是否为当前岗位所在节点 */
        private Boolean current;
    }

    /** 一条换岗路径 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransitionPath {
        /** 路径名称（如"转向 DevOps/SRE"） */
        private String name;
        /** 目标岗位名称 */
        private String targetRole;
        /** 转岗难度：{@code easy} / {@code medium} / {@code hard} */
        private String difficulty;
        /** 转岗理由（为什么这条路径可行） */
        private String reason;
        /** 衔接技能（需要在转岗中补齐/迁移的关键技能） */
        private List<String> bridgingSkills;
        /** 路径节点（≥2 个，起点通常为当前岗位） */
        private List<TransitionNode> nodes;
    }

    /** 换岗路径中的单个岗位节点 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransitionNode {
        /** 岗位名称 */
        private String title;
        /** 归一化角色码 */
        private String roleType;
        /** 在该节点下的职责/说明 */
        private String description;
    }
}
