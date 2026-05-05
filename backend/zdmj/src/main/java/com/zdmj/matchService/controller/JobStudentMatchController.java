package com.zdmj.matchService.controller;

import com.zdmj.common.model.Result;
import com.zdmj.matchService.dto.JobStudentMatchDTO;
import com.zdmj.matchService.dto.JobStudentMatchGenerateReqDTO;
import com.zdmj.matchService.dto.MatchWeightConfigDTO;
import com.zdmj.matchService.service.JobStudentMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人岗匹配分析控制器。
 *
 * <p>对应赛题《题目.md》：「职业探索与岗位匹配」「人岗匹配准确性判断」。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/matches")
public class JobStudentMatchController {

    private final JobStudentMatchService matchService;

    /**
     * 仅查询当前用户与该岗位的最新匹配结果（不触发 LLM 生成；不存在返回 null）。
     *
     * @param jobId 岗位ID
     * @return 匹配结果或 null
     */
    @GetMapping("/jobs/{jobId}")
    public Result<JobStudentMatchDTO> query(@PathVariable Long jobId) {
        return Result.success("查询人岗匹配成功", matchService.getOrNull(jobId));
    }

    /**
     * 生成人岗匹配分析（覆盖式）。
     *
     * <p>请求体可选携带 {@code weights} 临时覆盖默认权重；若学生画像缺失会抛
     * {@code MATCH_PRECONDITION_MISSING}（前端应引导去能力画像页生成）。</p>
     *
     * @param jobId 岗位ID
     * @param req   生成请求体（可选，可携带自定义权重）
     * @return 匹配结果
     */
    @PostMapping("/jobs/{jobId}")
    public Result<JobStudentMatchDTO> generate(@PathVariable Long jobId,
                                               @RequestBody(required = false) JobStudentMatchGenerateReqDTO req) {
        log.info("生成人岗匹配分析: jobId={}", jobId);
        return Result.success("生成人岗匹配成功", matchService.generate(jobId, req));
    }

    /**
     * 获取该岗位的默认权重（用于前端权重调节面板初始化）。
     *
     * @param jobId 岗位ID
     * @return 默认权重
     */
    @GetMapping("/jobs/{jobId}/weights")
    public Result<MatchWeightConfigDTO> defaultWeights(@PathVariable Long jobId) {
        return Result.success("查询默认权重成功", matchService.getDefaultWeights(jobId));
    }
}
