package com.zdmj.matchService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 人岗匹配分析结果 DTO。
 *
 * <p>该 DTO 同时承担两个角色：</p>
 * <ol>
 *   <li>作为 LLM 结构化输出（{@code chatStructuredOnce}）的目标 POJO；</li>
 *   <li>作为前端展示的接口响应。</li>
 * </ol>
 *
 * <p>因此各字段定义需要与 Prompt 中要求的 JSON Schema 严格一致。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobStudentMatchResponse {

    /**
     * 岗位ID（接口出参，便于前端复用；LLM 不必输出）。
     */
    private Long jobId;

    /**
     * 岗位类型展示值（如 java-backend）。
     */
    private String targetRoleType;

    /**
     * 综合匹配度（0~100，整数）。
     *
     * <p>该值由后端按 {@code weights} 对四维 score 加权重算后写入；LLM 即使输出也会被覆盖，
     * 以保证综合分严格符合权重设置。</p>
     */
    private Integer overallScore;

    /**
     * 四个维度的对比明细（key 与 {@link com.zdmj.matchService.enums.MatchDimension#getCode()} 一致）。
     *
     * <p>合法 key：basic / professionalSkill / professionalQuality / developmentPotential。</p>
     */
    private Map<String, DimensionMatchResponse> dimensions;

    /**
     * 本次匹配采用的权重配置快照。
     */
    private MatchWeightConfigResponse weights;

    /**
     * 命中亮点：学生在该岗位上的强匹配能力点（3~6 条）。
     */
    private List<String> matchedHighlights;

    /**
     * 关键差距：影响录用决策的主要短板（3~6 条）。
     */
    private List<String> criticalGaps;

    /**
     * 命中的岗位关键词（基于 jobs.keywords，模型与后端共同维护）。
     */
    private List<String> matchedKeywords;

    /**
     * 缺失的岗位关键词（基于 jobs.keywords 中未在学生画像中找到证据的词）。
     */
    private List<String> missingKeywords;

    /**
     * 关键技能匹配率（0~1）。
     *
     * <p>赛题指标：≥ 0.80。后端会基于 {@code matchedKeywords} 与岗位 keywords 总数兜底重算，
     * 避免完全依赖 LLM 自报。</p>
     */
    private BigDecimal keySkillMatchRate;

    /**
     * 一句话总结：可解释的总评，覆盖匹配亮点与关键差距。
     */
    private String summary;
}
