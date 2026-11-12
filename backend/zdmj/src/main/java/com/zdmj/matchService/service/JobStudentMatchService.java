package com.zdmj.matchService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.model.PageDTO;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.dto.MatchWeightConfigResponse;

/**
 * 人岗匹配分析服务：对照岗位描述与学生画像做四维对比，输出可解释的匹配结果与综合分。
 */
public interface JobStudentMatchService {

    /**
     * 分页查询当前用户已匹配过的岗位记录（仅最新一次；岗位已删则不返回）。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param page  页码（从 1 开始，可空）
     * @param limit 每页条数（可空）
     * @return 分页列表
     */
    PageDTO<JobStudentMatchListItemResponse> getMyPage(CurrentActor actor, Integer page, Integer limit);

    /**
     * 仅查询当前用户与该岗位的最新匹配结果；不存在返回 null。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param jobId 岗位ID
     * @return 匹配结果 DTO 或 null
     */
    JobStudentMatchResponse getOrNull(CurrentActor actor, Long jobId);

    /**
     * 生成人岗匹配分析（覆盖式：upsert by user_id + job_id）。
     *
     * <p>编排流程：</p>
     * <ol>
     *   <li>取学生画像（不存在则抛 {@code MATCH_PRECONDITION_MISSING}）；</li>
     *   <li>按当前岗位描述识别方向，并据此选择提示词和默认权重；</li>
     *   <li>解析权重（默认权重 + 请求体覆盖 → 归一化）；</li>
     *   <li>调用模型输出结构化匹配结果；</li>
     *   <li>后端兜底重算关键技能匹配率 与 综合分；</li>
     *   <li>再次确认岗位仍存在后，短事务按用户与岗位覆盖保存。</li>
     * </ol>
     *
     * @param actor 已认证操作者，包含用户编号
     * @param jobId 岗位ID
     * @param req   生成请求体（可携带自定义权重，可为 null）
     * @return 匹配结果 DTO
     */
    JobStudentMatchResponse generate(CurrentActor actor, Long jobId, JobStudentMatchGenerateRequest req);

    /**
     * 根据当前岗位描述识别方向后返回默认权重。关键词不足时调用方向识别模型，不写入匹配结果。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param jobId 岗位ID
     * @return 该岗位的默认权重
     */
    MatchWeightConfigResponse getDefaultWeights(CurrentActor actor, Long jobId);
}
