package com.zdmj.careerReportService.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.careerReportService.dto.CareerReportResponse;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.dto.CareerReportPolishRequest;
import com.zdmj.careerReportService.dto.CareerReportUpdateRequest;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.model.Result;

import java.util.concurrent.TimeUnit;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 职业发展报告控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/career-reports")
@Tag(name = "职业发展报告", description = "报告生成、润色、完整性检查与编辑")
public class CareerDevelopmentReportController {

    private final CareerDevelopmentReportService reportService;

    /**
     * 查询当前用户针对某岗位的最新职业发展报告（不触发生成；不存在返回 null）。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param jobId 岗位ID
     * @return 最新报告或 null
     */
    @GetMapping("/jobs/{jobId}")
    public Result<CareerReportResponse> queryLatest(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long jobId) {
        log.info("查询职业发展报告: jobId={}", jobId);
        return Result.success("查询职业发展报告成功", reportService.getLatestOrNull(actor, jobId));
    }

    /**
     * 生成职业发展报告并覆盖保存。学生画像缺失时返回 {@code MATCH_PRECONDITION_MISSING}。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param jobId 岗位ID
     * @param req   生成请求（可选：用户偏好、生成侧重点）
     * @return 保存后的报告
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 5, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/jobs/{jobId}")
    public Result<CareerReportResponse> generate(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long jobId,
                                            @RequestBody(required = false) CareerReportGenerateRequest req) {
        log.info("生成职业发展报告: jobId={}", jobId);
        return Result.success("生成职业发展报告成功", reportService.generate(actor, jobId, req));
    }

    /**
     * 润色指定报告并覆盖保存。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id  报告ID
     * @param req 润色请求（可选：润色说明）
     * @return 润色后的报告
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/{id}/polish")
    public Result<CareerReportResponse> polish(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long id,
                                          @RequestBody(required = false) CareerReportPolishRequest req) {
        log.info("润色职业发展报告: reportId={}", id);
        return Result.success("润色职业发展报告成功", reportService.polish(actor, id, req));
    }

    /**
     * 检查报告完整性，并把结果写回当前报告。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 报告ID
     * @return 检查结果
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/{id}/integrity-check")
    public Result<CareerReportCheckResponse> integrityCheck(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long id) {
        log.info("检查职业发展报告完整性: reportId={}", id);
        return Result.success("检查职业发展报告完整性成功", reportService.checkIntegrity(actor, id));
    }

    /**
     * 保存手动编辑后的报告内容，并覆盖原报告。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id  报告ID
     * @param req 编辑后的结构化正文
     * @return 保存后的报告
     */
    @PutMapping("/{id}")
    public Result<CareerReportResponse> saveManualEdit(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long id,
                                                  @RequestBody CareerReportUpdateRequest req) {
        log.info("保存职业发展报告手动编辑: reportId={}", id);
        return Result.success("保存职业发展报告成功", reportService.saveManualEdit(actor, id, req));
    }
}
