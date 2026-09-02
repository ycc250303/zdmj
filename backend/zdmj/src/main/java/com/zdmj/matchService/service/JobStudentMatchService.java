package com.zdmj.matchService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.common.model.PageDTO;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.dto.MatchWeightConfigResponse;
import com.zdmj.matchService.entity.JobStudentMatch;

/**
 * 人岗匹配分析服务：基于岗位画像与学生画像做四维对比，输出可解释的匹配结果与综合分。
 */
public interface JobStudentMatchService extends IService<JobStudentMatch> {

    /**
     * 分页查询当前用户已匹配过的岗位记录（仅最新一次；岗位已删则不返回）。
     *
     * @param page  页码（从 1 开始，可空）
     * @param limit 每页条数（可空）
     * @return 分页列表
     */
    PageDTO<JobStudentMatchListItemResponse> getMyPage(Integer page, Integer limit);

    /**
     * 仅查询当前用户与该岗位的最新匹配结果；不存在返回 null。
     *
     * @param jobId 岗位ID
     * @return 匹配结果 DTO 或 null
     */
    JobStudentMatchResponse getOrNull(Long jobId);

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
    JobStudentMatchResponse generate(Long jobId, JobStudentMatchGenerateRequest req);

    /**
     * 仅根据岗位类型解析默认权重（不查 LLM、不落库）。
     *
     * @param jobId 岗位ID
     * @return 该岗位的默认权重
     */
    MatchWeightConfigResponse getDefaultWeights(Long jobId);
}
