package com.zdmj.matchService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.matchService.dto.JobStudentMatchDTO;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.dto.MatchWeightConfigDTO;
import com.zdmj.matchService.entity.JobStudentMatch;

/**
 * 人岗匹配分析服务。
 *
 * <p>此模块对应赛题《题目.md》中：</p>
 * <ul>
 *   <li>4) 构建学生职业生涯发展报告 - a) 职业探索与岗位匹配</li>
 *   <li>技术指标 - 人岗匹配准确性判断</li>
 * </ul>
 */
public interface JobStudentMatchService extends IService<JobStudentMatch> {

    /**
     * 仅查询当前用户与该岗位的最新匹配结果；不存在返回 null。
     *
     * @param jobId 岗位ID
     * @return 匹配结果 DTO 或 null
     */
    JobStudentMatchDTO getOrNull(Long jobId);

    /**
     * 生成人岗匹配分析（覆盖式：upsert by user_id + job_id）。
     *
     * <p>编排流程：</p>
     * <ol>
     *   <li>取岗位画像（不存在则自动调用 jobCapabilityProfileService 生成）；</li>
     *   <li>取学生画像（不存在则抛 {@code MATCH_PRECONDITION_MISSING}）；</li>
     *   <li>解析权重（默认权重 + 请求体覆盖 → 归一化）；</li>
     *   <li>路由 Prompt（按岗位 targetRoleType）→ 调 LLM 结构化输出；</li>
     *   <li>后端兜底重算关键技能匹配率 与 综合分；</li>
     *   <li>落库 upsert，返回完整 DTO。</li>
     * </ol>
     *
     * @param jobId 岗位ID
     * @param req   生成请求体（可携带自定义权重，可为 null）
     * @return 匹配结果 DTO
     */
    JobStudentMatchDTO generate(Long jobId, JobStudentMatchGenerateRequest req);

    /**
     * 仅根据岗位类型解析默认权重（不查 LLM、不落库）。
     *
     * @param jobId 岗位ID
     * @return 该岗位的默认权重
     */
    MatchWeightConfigDTO getDefaultWeights(Long jobId);
}
