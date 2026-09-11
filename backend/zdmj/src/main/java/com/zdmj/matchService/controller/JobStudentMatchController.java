package com.zdmj.matchService.controller;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.async.AsyncTaskDTO;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;

import java.util.concurrent.TimeUnit;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.JobStudentMatchGenerateRequest;
import com.zdmj.matchService.dto.JobStudentMatchListItemResponse;
import com.zdmj.matchService.dto.MatchWeightConfigResponse;
import com.zdmj.matchService.service.JobStudentMatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人岗匹配分析控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/matches")
@Tag(name = "人岗匹配", description = "匹配分析、权重配置与结果查询")
public class JobStudentMatchController {

    private final JobStudentMatchService matchService;

    /**
     * 分页查询当前用户已匹配过的岗位记录（按最近匹配时间倒序；岗位已删则不返回）。
     *
     * @param page  页码（从 1 开始）
     * @param limit 每页条数
     * @return 分页列表
     */
    @GetMapping
    public Result<PageDTO<JobStudentMatchListItemResponse>> getMyPage(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Result.success("查询匹配记录成功", matchService.getMyPage(page, limit));
    }

    /**
     * 仅查询当前用户与该岗位的最新匹配结果（不触发 LLM 生成；不存在返回 null）。
     *
     * @param jobId 岗位ID
     * @return 匹配结果或 null
     */
    @GetMapping("/jobs/{jobId}")
    public Result<JobStudentMatchResponse> query(@PathVariable Long jobId) {
        return Result.success("查询人岗匹配成功", matchService.getOrNull(jobId));
    }

    /**
     * 入队生成人岗匹配。学生画像缺失立即 {@code MATCH_PRECONDITION_MISSING}。
     *
     * @param jobId 岗位ID
     * @param req   生成请求体（可选，可携带自定义权重）
     * @return 异步任务
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/jobs/{jobId}")
    public Result<AsyncTaskDTO> generate(@PathVariable Long jobId,
                                               @RequestBody(required = false) JobStudentMatchGenerateRequest req) {
        log.info("入队人岗匹配: jobId={}", jobId);
        return Result.success("已提交人岗匹配任务", matchService.enqueueGenerate(jobId, req));
    }

    /**
     * 获取该岗位的默认权重（用于前端权重调节面板初始化）。
     *
     * @param jobId 岗位ID
     * @return 默认权重
     */
    @GetMapping("/jobs/{jobId}/weights")
    public Result<MatchWeightConfigResponse> defaultWeights(@PathVariable Long jobId) {
        return Result.success("查询默认权重成功", matchService.getDefaultWeights(jobId));
    }
}
