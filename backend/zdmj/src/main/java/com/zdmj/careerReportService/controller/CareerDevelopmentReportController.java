package com.zdmj.careerReportService.controller;

import com.zdmj.careerReportService.dto.CareerReportCheckDTO;
import com.zdmj.careerReportService.dto.CareerReportDTO;
import com.zdmj.careerReportService.dto.CareerReportGenerateReqDTO;
import com.zdmj.careerReportService.dto.CareerReportPolishReqDTO;
import com.zdmj.careerReportService.dto.CareerReportUpdateReqDTO;
import com.zdmj.careerReportService.service.CareerDevelopmentReportService;
import com.zdmj.common.model.Result;
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
     * @param jobId 岗位ID
     * @return 最新报告或 null
     */
    @GetMapping("/jobs/{jobId}")
    public Result<CareerReportDTO> queryLatest(@PathVariable Long jobId) {
        log.info("查询职业发展报告: jobId={}", jobId);
        return Result.success("查询职业发展报告成功", reportService.getLatestOrNull(jobId));
    }

    /**
     * 生成职业发展报告（同步）。
     *
     * <p>聚合学生画像、岗位画像、人岗匹配、岗位图谱与知识库 RAG 后调用大模型生成结构化报告，
     * 并写入新版本记录。若学生画像缺失会抛 {@code MATCH_PRECONDITION_MISSING}。</p>
     *
     * @param jobId 岗位ID
     * @param req   生成请求（可选：用户偏好、生成侧重点）
     * @return 新生成的报告
     */
    @PostMapping("/jobs/{jobId}")
    public Result<CareerReportDTO> generate(@PathVariable Long jobId,
                                            @RequestBody(required = false) CareerReportGenerateReqDTO req) {
        log.info("生成职业发展报告: jobId={}", jobId);
        return Result.success("生成职业发展报告成功", reportService.generate(jobId, req));
    }

    /**
     * 对指定报告进行智能润色（生成新版本）。
     *
     * @param id  报告ID
     * @param req 润色请求（可选：润色说明）
     * @return 润色后的新版本报告
     */
    @PostMapping("/{id}/polish")
    public Result<CareerReportDTO> polish(@PathVariable Long id,
                                          @RequestBody(required = false) CareerReportPolishReqDTO req) {
        log.info("润色职业发展报告: reportId={}", id);
        return Result.success("润色职业发展报告成功", reportService.polish(id, req));
    }

    /**
     * 完整性检查（本地规则 + 大模型复核，结果写回当前报告记录）。
     *
     * @param id 报告ID
     * @return 检查结果（完整度、缺失章节、风险等级等）
     */
    @PostMapping("/{id}/integrity-check")
    public Result<CareerReportCheckDTO> integrityCheck(@PathVariable Long id) {
        log.info("检查职业发展报告完整性: reportId={}", id);
        return Result.success("检查职业发展报告完整性成功", reportService.checkIntegrity(id));
    }

    /**
     * 保存手动编辑后的报告内容（生成新版本）。
     *
     * @param id  报告ID
     * @param req 编辑后的结构化正文
     * @return 保存后的新版本报告
     */
    @PutMapping("/{id}")
    public Result<CareerReportDTO> saveManualEdit(@PathVariable Long id,
                                                  @RequestBody CareerReportUpdateReqDTO req) {
        log.info("保存职业发展报告手动编辑: reportId={}", id);
        return Result.success("保存职业发展报告成功", reportService.saveManualEdit(id, req));
    }
}
