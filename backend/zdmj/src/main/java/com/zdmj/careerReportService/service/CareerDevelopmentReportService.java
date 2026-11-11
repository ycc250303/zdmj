package com.zdmj.careerReportService.service;

import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.careerReportService.dto.CareerReportResponse;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.careerReportService.dto.CareerReportPolishRequest;
import com.zdmj.careerReportService.dto.CareerReportUpdateRequest;

/**
 * 职业发展报告服务接口
 */
public interface CareerDevelopmentReportService {

    /**
     * 查询当前用户针对某岗位的报告（不触发生成）。
     *
     * @param jobId 岗位ID
     * @return 当前报告；不存在时返回 null
     */
    CareerReportResponse getLatestOrNull(Long jobId);

    /**
     * 同步生成职业发展报告并覆盖保存。
     * 模型调用在事务外完成，插入或覆盖在同一短事务中完成。
     *
     * @param jobId 岗位ID
     * @param req   生成参数（可选）
     * @return 保存后的报告
     */
    CareerReportResponse generate(Long jobId, CareerReportGenerateRequest req);

    /**
     * 对已有报告进行智能润色，并覆盖原报告。
     * 模型调用在事务外完成，覆盖写入在同一短事务中完成。
     *
     * @param reportId 报告ID
     * @param req      润色参数（可选）
     * @return 润色后的报告
     */
    CareerReportResponse polish(Long reportId, CareerReportPolishRequest req);

    /**
     * 对报告做完整性检查（本地 + LLM），并在短事务中更新当前记录的质量标记。
     *
     * @param reportId 报告ID
     * @return 检查结果
     */
    CareerReportCheckResponse checkIntegrity(Long reportId);

    /**
     * 保存用户手动编辑后的报告正文，并覆盖原报告。
     * 覆盖写入在同一短事务中完成。
     *
     * @param reportId 报告ID
     * @param req      编辑内容
     * @return 保存后的报告
     */
    CareerReportResponse saveManualEdit(Long reportId, CareerReportUpdateRequest req);
}
